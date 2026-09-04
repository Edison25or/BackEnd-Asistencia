package com.idat.asistencia.service.impl;

import com.idat.asistencia.dto.ConsolidadoDTOs.*;
import com.idat.asistencia.exception.BusinessException;
import com.idat.asistencia.exception.ResourceNotFoundException;
import com.idat.asistencia.model.entity.*;
import com.idat.asistencia.model.enums.*;
import com.idat.asistencia.repository.*;
import com.idat.asistencia.security.SecurityHelper;
import com.idat.asistencia.service.AuditoriaService;
import com.idat.asistencia.service.ConsolidadoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generacion y cierre del consolidado quincenal (CU21, CU23).
 *
 * ============================================================
 * QUE SE ELIMINA DEL PROTOTIPO
 * ============================================================
 * Todo el subsistema de bolsa de horas (entrada, acumulada, consumida,
 * salida), los tramos de recargo Tasa A del 25 por ciento y Tasa B del 35
 * por ciento con su constante TOPE_TASA_A_MIN, el bono en soles y los
 * minutos a descontar.
 *
 * Contradicen el alcance: el sistema no calcula montos de pago (AL-01) ni
 * tramos de recargo porcentual (AL-04). Contabilidad hace ese calculo
 * fuera del sistema con el consolidado exportado (DEP-05).
 *
 * ============================================================
 * QUE SE CORRIGE
 * ============================================================
 * 1. Los totales pasan de columnas fijas a filas de ConsolidadoTurno, una
 *    por combinacion de turno y condicion de feriado. Con columnas,
 *    agregar el desglose de feriado habria llevado el consolidado de seis
 *    a diez columnas, y un tercer turno habria exigido migrar la tabla.
 *
 * 2. Las asistencias incluidas pasan a estado CONSOLIDADO. En el
 *    prototipo se quedaban en REVISADO indefinidamente, de modo que
 *    nunca quedaba registro de que ya habian sido liquidadas.
 *
 * 3. El criterio de bloqueo deja de ser "cualquier estado distinto de
 *    REVISADO o CONSOLIDADO". Como ninguna jornada normal llegaba a
 *    REVISADO por si sola, el cierre quedaba bloqueado de forma
 *    permanente.
 *
 * 4. La reapertura es directa, en un solo paso (RN-38). Desaparecen
 *    solicitarReapertura() y aprobarReapertura(), que implementaban un
 *    flujo de dos pasos no contemplado y que ni siquiera registraba quien
 *    solicitaba.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsolidadoServiceImpl implements ConsolidadoService {

    private final ConsolidadoRepository       consolidadoRepo;
    private final ConsolidadoTurnoRepository  consolidadoTurnoRepo;
    private final AsistenciaRepository        asistenciaRepo;
    private final QuincenaRepository          quincenaRepo;
    private final SecurityHelper              securityHelper;
    private final AuditoriaService            auditoria;

    private static final String TABLA = "consolidado_quincena";

    // ════════════════════════════════════════════════════════════
    // GENERAR (CU21)
    // ════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public List<ConsolidadoResponse> generarConsolidado(Long idQuincena) {
        Quincena q = buscarQuincena(idQuincena);

        // ---- 1. Bloqueo por pendientes (RN-37) ----
        long bloqueantes = asistenciaRepo.countBloqueantes(idQuincena);
        if (bloqueantes > 0) {
            List<Asistencia> detalle = asistenciaRepo.findBloqueantes(idQuincena);
            String resumen = detalle.stream().limit(5)
                    .map(a -> a.getTrabajador().getNombreCompleto()
                            + " (" + a.getFecha() + ", " + a.getTipo() + ")")
                    .collect(Collectors.joining("; "));
            throw new BusinessException(
                    "No se puede generar el consolidado: hay " + bloqueantes
                            + " registro(s) pendientes de revision. Ejemplos: " + resumen
                            + (bloqueantes > 5 ? " y " + (bloqueantes - 5) + " mas." : "."));
        }

        List<Asistencia> asistencias = asistenciaRepo.findByQuincena(idQuincena);
        if (asistencias.isEmpty())
            throw new BusinessException("La quincena no tiene registros de asistencia.");

        Usuario actor = securityHelper.getUsuarioAutenticado();
        LocalDateTime ahora = LocalDateTime.now();

        // ---- 2. Agrupar por trabajador ----
        Map<Long, List<Asistencia>> porTrabajador = asistencias.stream()
                .collect(Collectors.groupingBy(a -> a.getTrabajador().getIdTrabajador()));

        List<ConsolidadoQuincena> resultado = new ArrayList<>();

        for (var entrada : porTrabajador.entrySet()) {
            List<Asistencia> jornadas = entrada.getValue();
            Trabajador t = jornadas.get(0).getTrabajador();

            // Version anterior, si se esta regenerando tras una
            // reapertura. Se conserva marcada como REEMPLAZADO para que
            // ambas queden trazables (RN-38).
            int version = 1;
            Optional<ConsolidadoQuincena> previo = consolidadoRepo
                    .findVigentePorQuincenaYTrabajador(idQuincena, t.getIdTrabajador());
            if (previo.isPresent()) {
                ConsolidadoQuincena viejo = previo.get();
                viejo.setEstado(EstadoConsolidado.REEMPLAZADO);
                consolidadoRepo.save(viejo);
                version = viejo.getVersion() + 1;
            }

            // El consolidado nace CERRADO, no BORRADOR.
            //
            // Generarlo y cerrar la quincena son la misma operacion
            // (RN-36): no existe un paso intermedio en el que el
            // consolidado exista pero admita cambios. Dejarlo en BORRADOR
            // describia un estado que el sistema nunca alcanza, y ademas
            // contradecia a la quincena, que si quedaba CERRADA.
            //
            // La consecuencia practica era que editarConsolidado() no
            // rechazaba la modificacion, porque su guarda comprueba
            // justamente que el estado sea CERRADO.
            ConsolidadoQuincena c = ConsolidadoQuincena.builder()
                    .quincena(q)
                    .trabajador(t)
                    .version(version)
                    .estado(EstadoConsolidado.CERRADO)
                    .generadoEn(ahora)
                    .generadoPor(actor)
                    .cerradoEn(ahora)
                    .build();

            acumular(c, jornadas);
            resultado.add(consolidadoRepo.save(c));
        }

        // ---- 3. Marcar las asistencias como consolidadas ----
        for (Asistencia a : asistencias) {
            a.setEstado(EstadoAsistencia.CONSOLIDADO);
            asistenciaRepo.save(a);
        }

        // ---- 4. Cerrar la quincena (RN-36) ----
        q.setEstado(EstadoQuincena.CERRADA);
        q.setCerradoPor(actor);
        q.setCerradoEn(ahora);
        quincenaRepo.save(q);

        auditoria.registrarCampo(TABLA, idQuincena, "GENERAR_CONSOLIDADO",
                "estado_quincena", "ABIERTA",
                "CERRADA - " + resultado.size() + " consolidados generados");

        return resultado.stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * Acumula las jornadas de un trabajador en filas por turno y
     * condicion de feriado.
     *
     * ============================================================
     * LOS BUCKETS SON EXCLUYENTES
     * ============================================================
     * Los minutos trabajados dentro de un dia feriado se RESTAN del total
     * normal del turno y se SUMAN a la fila de feriado del mismo turno.
     * Contarlos en ambos lados duplicaria las horas del consolidado.
     *
     * Una jornada nocturna que empieza la vispera de un feriado aporta
     * minutos a las dos filas: los previos a la medianoche a la normal y
     * los posteriores a la de feriado (RN-41).
     */
    private void acumular(ConsolidadoQuincena c, List<Asistencia> jornadas) {
        int diasFalta = 0, diasPermiso = 0, diasFaltaJust = 0;
        int totalTardanza = 0, totalSalTemprana = 0;
        int minTrabajados = 0, minEsperados = 0;

        for (Asistencia a : jornadas) {

            // ---- Conteos por dia ----
            //
            // La ausencia solo cuenta como tal si el trabajador NO vino.
            // Un permiso no impide marcar: si el trabajador se presenta
            // igual, la jornada real manda sobre la ausencia declarada.
            //
            // Sin la comprobacion de ingresoReal, alguien con permiso que
            // decide venir a trabajar veia sus horas descartadas: el
            // consolidado contaba el dia como permiso y saltaba antes de
            // sumar nada. Trabajaba y no se le pagaba.
            boolean vinoATrabajar = a.getIngresoReal() != null;

            if (a.getTipo() == TipoRegistro.FALTA_INJUSTIFICADA) { diasFalta++; continue; }

            if (!vinoATrabajar) {
                if (a.getPermiso()          != null) { diasPermiso++;   continue; }
                if (a.getFaltaJustificada() != null) { diasFaltaJust++; continue; }
            } else if (a.getPermiso() != null || a.getFaltaJustificada() != null) {
                // Trabajo pese a tener ausencia registrada. Las horas
                // cuentan, y se deja constancia para que Contabilidad no
                // lo lea como un error de captura.
                c.setObservaciones(concatenar(c.getObservaciones(),
                        "Jornada " + a.getFecha() + " trabajada pese a tener "
                                + (a.getPermiso() != null ? "permiso" : "falta justificada")
                                + " registrado."));
            }

            totalTardanza    += nz(a.getMinTardanza());
            totalSalTemprana += nz(a.getMinSalTemprana());
            minEsperados     += nz(a.getMinNetosProg()) + nz(a.getMinExtraProg());

            int totales = nz(a.getMinHorasTotales());
            if (totales == 0) continue;

            minTrabajados += totales;

            Turno turno = a.getTurno();
            if (turno == null) {
                // Sin turno asignado no se puede clasificar. El Jefe debe
                // asignarlo al resolver la jornada (RN-25). Se registra
                // para no perder los minutos en silencio.
                log.warn("Asistencia {} sin turno asignado: {} minutos sin clasificar",
                        a.getIdAsistencia(), totales);
                c.setObservaciones(concatenar(c.getObservaciones(),
                        "Jornada " + a.getFecha() + " sin turno asignado ("
                                + totales + " min sin clasificar)."));
                continue;
            }

            // Hora extra reconocida: estructural mas excepcional aprobada.
            // El consolidado las reporta en un total unico; la distincion
            // se conserva en el registro diario y el reporte detallado.
            int extra = nz(a.getMinExtraProg());
            if (a.getResultadoValidacion() == ResultadoValidacion.APROBADO) {
                extra += nz(a.getValMinPrevIng()) + nz(a.getValMinPostSal());
            }
            extra = Math.min(extra, totales);
            int normales = totales - extra;

            // Reparto entre feriado y no feriado, en proporcion a los
            // minutos que cayeron dentro del dia feriado.
            int minFeriado = Math.min(nz(a.getMinutosFeriado()), totales);

            if (minFeriado <= 0) {
                c.acumular(turno, false, normales, extra);
            } else if (minFeriado >= totales) {
                c.acumular(turno, true, normales, extra);
            } else {
                double proporcion  = (double) minFeriado / totales;
                int normalesFer    = (int) Math.round(normales * proporcion);
                int extraFer       = (int) Math.round(extra    * proporcion);
                c.acumular(turno, true,  normalesFer,            extraFer);
                c.acumular(turno, false, normales - normalesFer, extra - extraFer);
            }
        }

        c.setDiasFalta(diasFalta);
        c.setDiasPermiso(diasPermiso);
        c.setDiasFaltaJustificada(diasFaltaJust);
        c.setMinTotalTardanza(totalTardanza);
        c.setMinTotalSalTemprana(totalSalTemprana);
        c.setMinAcumuladoVsEsperado(minTrabajados - minEsperados);
    }

    // ════════════════════════════════════════════════════════════
    // CONSULTAS
    // ════════════════════════════════════════════════════════════

    @Override
    public List<ConsolidadoResponse> getConsolidado(Long idQuincena) {
        return consolidadoRepo.findVigentesPorQuincena(idQuincena)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public ConsolidadoResponse getConsolidadoTrabajador(Long idQuincena, Long idTrabajador) {
        securityHelper.verificarAccesoPropioOAdmin(idTrabajador);
        return consolidadoRepo.findVigentePorQuincenaYTrabajador(idQuincena, idTrabajador)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe consolidado para ese trabajador en la quincena."));
    }

    /**
     * Calcula el consolidado SIN persistirlo ni cerrar la quincena.
     *
     * Generar el consolidado cierra la quincena de forma irreversible
     * salvo reapertura (RN-36), asi que quien lo ejecuta necesita poder
     * ver el resultado antes de comprometerse. Sin esta operacion, la
     * unica manera de saber que produce el calculo es ejecutarlo.
     *
     * Reutiliza el mismo acumular() que generar(), de modo que la vista
     * previa y el consolidado definitivo no pueden divergir.
     *
     * A diferencia de generar(), NO rechaza por pendientes de revision:
     * su proposito es justamente mostrar como va quedando el periodo
     * mientras se resuelven.
     */
    @Override
    public List<ConsolidadoResponse> previsualizar(Long idQuincena) {
        // El control de rol lo aplica el @PreAuthorize del controlador,
        // igual que en generar(). SecurityHelper no expone una
        // comprobacion de rol administrativo, y anadirla aqui duplicaria
        // en el servicio una regla que ya vive en la capa de entrada.
        Quincena q = buscarQuincena(idQuincena);

        List<Asistencia> asistencias = asistenciaRepo.findByQuincena(idQuincena);
        if (asistencias.isEmpty())
            throw new BusinessException("La quincena no tiene registros de asistencia.");

        Map<Long, List<Asistencia>> porTrabajador = asistencias.stream()
                .collect(Collectors.groupingBy(a -> a.getTrabajador().getIdTrabajador()));

        List<ConsolidadoResponse> salida = new ArrayList<>();

        for (var entrada : porTrabajador.entrySet()) {
            List<Asistencia> jornadas = entrada.getValue();

            ConsolidadoQuincena c = ConsolidadoQuincena.builder()
                    .quincena(q)
                    .trabajador(jornadas.get(0).getTrabajador())
                    .version(0)                       // 0 = no persistido
                    .estado(EstadoConsolidado.BORRADOR)
                    .build();

            acumular(c, jornadas);
            salida.add(toResponse(c));
        }

        salida.sort(Comparator.comparing(ConsolidadoResponse::getTrabajadorNombre));
        return salida;
    }

    @Override
    @Transactional
    public ConsolidadoResponse editar(Long idConsolidado, EditarConsolidadoRequest req) {
        ConsolidadoQuincena c = consolidadoRepo.findById(idConsolidado)
                .orElseThrow(() -> new ResourceNotFoundException("Consolidado no encontrado."));

        // Un consolidado CERRADO SI admite editar su observacion.
        //
        // RN-36 vuelve definitiva la informacion para el pago, es decir
        // las HORAS. La observacion es una nota para Contabilidad, no un
        // valor calculado, y ningun metodo del servicio permite alterar
        // los minutos de un consolidado ya generado: para eso hay que
        // reabrir la quincena y regenerarlo.
        //
        // Condicionarla al estado del consolidado o de la quincena dejaba
        // el metodo inservible: como generar y cerrar son la misma
        // operacion, no existe momento alguno en que la nota pudiera
        // escribirse.
        //
        // Lo que si se rechaza es editar una version reemplazada, porque
        // ya no es el documento vigente del periodo.
        if (c.getEstado() == EstadoConsolidado.REEMPLAZADO)
            throw new BusinessException(
                    "Este consolidado fue reemplazado por una version posterior. "
                            + "Edita la vigente.");

        String anterior = c.getObservaciones();
        c.setObservaciones(req.getObservaciones());
        ConsolidadoQuincena guardado = consolidadoRepo.save(c);

        auditoria.registrarCampo(TABLA, idConsolidado, "MODIFICAR",
                "observaciones", anterior, req.getObservaciones());

        return toResponse(guardado);
    }

    @Override
    public List<QuincenaConsolidadoResumenDTO> getQuincenasConResumen() {
        return quincenaRepo.findAllByOrderByInicioDesc().stream().map(q -> {
            long bloqueantes = asistenciaRepo.countBloqueantes(q.getIdQuincena());
            return QuincenaConsolidadoResumenDTO.builder()
                    .idQuincena(q.getIdQuincena())
                    .descripcion(q.getDescripcion())
                    .inicio(q.getInicio().toString())
                    .fin(q.getFin().toString())
                    .estado(q.getEstado().name())
                    .totalConsolidados(consolidadoRepo.countByQuincena_IdQuincena(q.getIdQuincena()))
                    .bloqueantes(bloqueantes)
                    .puedeGenerarse(q.isAbierta() && bloqueantes == 0)
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public ConsolidadoReporteResponse getReporte(Long idQuincena) {
        Quincena q = buscarQuincena(idQuincena);
        List<ConsolidadoQuincena> lista = consolidadoRepo.findVigentesPorQuincena(idQuincena);

        int totNormales = 0, totExtra = 0, totFeriado = 0;
        int totFalta = 0, totPermiso = 0, totFaltaJust = 0;

        for (ConsolidadoQuincena c : lista) {
            totNormales  += c.getTotalNormalesMinutos();
            totExtra     += c.getTotalExtraMinutos();
            totFeriado   += c.getTotalFeriadoMinutos();
            totFalta     += nz(c.getDiasFalta());
            totPermiso   += nz(c.getDiasPermiso());
            totFaltaJust += nz(c.getDiasFaltaJustificada());
        }

        return ConsolidadoReporteResponse.builder()
                .idQuincena(idQuincena)
                .descripcion(q.getDescripcion())
                .inicio(q.getInicio().toString())
                .fin(q.getFin().toString())
                .estado(q.getEstado().name())
                .trabajadores(lista.stream().map(this::toResponse).collect(Collectors.toList()))
                .totalTrabajadores(lista.size())
                .totalHNormales(fmt(totNormales))
                .totalHExtra(fmt(totExtra))
                .totalHFeriado(fmt(totFeriado))
                .totalDiasFalta(totFalta)
                .totalDiasPermiso(totPermiso)
                .totalDiasFaltaJustificada(totFaltaJust)
                .build();
    }

    // ════════════════════════════════════════════════════════════
    // REAPERTURA (CU23, RN-38)
    // ════════════════════════════════════════════════════════════

    /**
     * Reapertura DIRECTA, en un solo paso, por el Superadministrador.
     *
     * El prototipo implementaba un flujo de dos pasos con un estado
     * REAPERTURA_PENDIENTE que el analisis no contempla, y que ademas no
     * registraba quien solicitaba ni validaba su rol.
     */
    @Override
    @Transactional
    public void reabrirQuincena(ReaperturaRequest req) {
        Quincena q = buscarQuincena(req.getIdQuincena());

        if (q.getEstado() != EstadoQuincena.CERRADA)
            throw new BusinessException("Solo se puede reabrir una quincena cerrada.");

        Usuario actor = securityHelper.getUsuarioAutenticado();

        q.setEstado(EstadoQuincena.ABIERTA);
        q.setReabiertoPor(actor);
        q.setReabiertoEn(LocalDateTime.now());
        q.setMotivoReapertura(req.getMotivo());
        quincenaRepo.save(q);

        // Las asistencias vuelven a REVISADO para poder corregirse. No a
        // CALCULADO: ya fueron revisadas, y devolverlas a ese estado las
        // haria aparecer como pendientes en la bandeja.
        for (Asistencia a : asistenciaRepo.findByQuincena(q.getIdQuincena())) {
            if (a.getEstado() == EstadoAsistencia.CONSOLIDADO) {
                a.setEstado(EstadoAsistencia.REVISADO);
                asistenciaRepo.save(a);
            }
        }

        auditoria.registrarCampo(TABLA, q.getIdQuincena(), "REABRIR",
                "estado_quincena", "CERRADA", "ABIERTA");
        auditoria.registrarConMotivo("quincenas", q.getIdQuincena(), "REABRIR", req.getMotivo());
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════

    private Quincena buscarQuincena(Long id) {
        return quincenaRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quincena no encontrada: " + id));
    }

    private ConsolidadoResponse toResponse(ConsolidadoQuincena c) {
        Trabajador t = c.getTrabajador();

        List<TotalTurnoDTO> totales = c.getTotalesPorTurno().stream()
                .sorted(Comparator
                        .comparing((ConsolidadoTurno f) -> f.getTurno().getNombre())
                        .thenComparing(ConsolidadoTurno::isEsFeriado))
                .map(f -> TotalTurnoDTO.builder()
                        .turno(f.getTurno().getNombre())
                        .esFeriado(f.isEsFeriado())
                        .minNormales(f.getMinNormales())
                        .minExtra(f.getMinExtra())
                        .hNormales(fmt(f.getMinNormales()))
                        .hExtra(fmt(f.getMinExtra()))
                        .build())
                .collect(Collectors.toList());

        return ConsolidadoResponse.builder()
                .id(c.getId())
                .idQuincena(c.getQuincena().getIdQuincena())
                .quincenaDescripcion(c.getQuincena().getDescripcion())
                .idTrabajador(t.getIdTrabajador())
                .trabajadorNombre(t.getNombreCompleto())
                .puestoNombre(t.getPuesto() != null ? t.getPuesto().getPuesto() : null)
                .areaNombre(t.getArea() != null ? t.getArea().getArea() : null)
                .totalesPorTurno(totales)
                .hTotalNormales(fmt(c.getTotalNormalesMinutos()))
                .hTotalExtra(fmt(c.getTotalExtraMinutos()))
                .hTotalFeriado(fmt(c.getTotalFeriadoMinutos()))
                .hTotalGeneral(fmt(c.getTotalNormalesMinutos() + c.getTotalExtraMinutos()))
                .diasFalta(c.getDiasFalta())
                .diasPermiso(c.getDiasPermiso())
                .diasFaltaJustificada(c.getDiasFaltaJustificada())
                .minTotalTardanza(c.getMinTotalTardanza())
                .minTotalSalTemprana(c.getMinTotalSalTemprana())
                .minAcumuladoVsEsperado(c.getMinAcumuladoVsEsperado())
                .hAcumuladoVsEsperado(fmtConSigno(c.getMinAcumuladoVsEsperado()))
                .observaciones(c.getObservaciones())
                .version(c.getVersion())
                .estado(c.getEstado().name())
                .generadoEn(c.getGeneradoEn() != null ? c.getGeneradoEn().toString() : null)
                .cerradoEn(c.getCerradoEn() != null ? c.getCerradoEn().toString() : null)
                .build();
    }

    private String fmt(int min) {
        return String.format("%02d:%02d", min / 60, Math.abs(min % 60));
    }

    private String fmtConSigno(Integer min) {
        int v = nz(min);
        return (v < 0 ? "-" : "+") + fmt(Math.abs(v));
    }

    private String concatenar(String previo, String nuevo) {
        if (previo == null || previo.isBlank()) return nuevo;
        return previo + " " + nuevo;
    }

    private int nz(Integer v) {
        return v != null ? v : 0;
    }
}
