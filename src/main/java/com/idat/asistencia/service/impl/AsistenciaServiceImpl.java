package com.idat.asistencia.service.impl;

import com.idat.asistencia.dto.AsistenciaDTOs;
import com.idat.asistencia.dto.AsistenciaDTOs.*;
import com.idat.asistencia.exception.BusinessException;
import com.idat.asistencia.exception.ResourceNotFoundException;
import com.idat.asistencia.model.entity.*;
import com.idat.asistencia.model.enums.*;
import com.idat.asistencia.repository.*;
import com.idat.asistencia.security.SecurityHelper;
import com.idat.asistencia.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Marcacion de asistencia y revision de jornadas (CU03, CU15, CU18, CU19,
 * CU20).
 *
 * ============================================================
 * QUE CAMBIA EN EL ALGORITMO DE MARCACION
 * ============================================================
 * 1. La jornada se resuelve por VENTANA HORARIA y no por "fecha = hoy".
 *    Un turno que entra a las 22:00 y sale a las 06:00 marca su salida al
 *    dia calendario siguiente: la busqueda por fecha no la encontraba y
 *    caia al camino de marcacion no programada.
 *
 * 2. Un solo codigo de barras. El prototipo parseaba el identificador del
 *    propio codigo escaneado y exigia sufijos IN u OU, es decir DOS
 *    codigos por trabajador, lo que RT-02 prohibe. Ahora entrada y salida
 *    se deducen del estado de la jornada.
 *
 * 3. La salida NUNCA se rechaza (RN-43). El prototipo la descartaba si
 *    excedia el parametro P2, dejando al trabajador sin registro de
 *    salida: la salida fisica ocurrio, y descartar la hora obliga a
 *    reconstruirla de memoria.
 *
 * 4. El tope combinado P3 se evalua en la SALIDA. En la entrada todavia
 *    no se conoce la hora de salida, de modo que la comprobacion que
 *    hacia el prototipo no podia funcionar.
 *
 * 5. Guarda anti-rebote antes de todo lo demas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AsistenciaServiceImpl implements AsistenciaService {

    private final AsistenciaRepository  asistenciaRepo;
    private final TrabajadorRepository  trabajadorRepo;
    private final QuincenaRepository    quincenaRepo;
    private final UsuarioRepository     usuarioRepo;
    private final ParametrosService     parametrosService;
    private final FeriadoService        feriadoService;
    private final PreRegistroService    preRegistroService;
    private final LectorEstadoService   lector;
    private final SecurityHelper        securityHelper;
    private final AuditoriaService      auditoria;

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final String TABLA = "asistencias";

    // ════════════════════════════════════════════════════════════
    // MARCAR (CU03) — endpoint del lector de planta
    // ════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public MarcarAsistenciaResponse marcar(String codigo) {
        if (codigo == null || codigo.isBlank())
            throw new BusinessException("Codigo invalido.");

        // Truncado al minuto A PROPOSITO.
        //
        // recalcularTiempos() hace tres restas independientes (duracion,
        // minutos previos y minutos posteriores) y Duration.toMinutes()
        // trunca los segundos en cada una por separado. Con una entrada a
        // las 22:21:30 y una salida a las 07:26:40, la duracion conserva
        // los segundos sobrantes (545 min) mientras que los previos los
        // pierden (23 en vez de 24), y el total sale un minuto de mas:
        // 8h16m donde deberia decir 8h15m.
        //
        // Truncar aqui, al registrar, hace que los tres calculos partan
        // del mismo dato. Un reloj de planta no necesita segundos.
        LocalDateTime ahora = LocalDateTime.now().withSecond(0).withNano(0);

        // ---- 1. Identificar al trabajador ----
        Trabajador t = trabajadorRepo.findByCodigoBarras(codigo.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado."));

        if (!t.isActivo())
            throw new BusinessException("El trabajador no esta activo.");

        ParametrosGeneralesAsistencia p = parametrosService.getGenerales();

        // ---- 2. Guarda anti-rebote (HU-53) ----
        // Se descarta en silencio: mostrar un error confundiria a quien
        // simplemente paso el carne dos veces sin querer.
        if (lector.esRebote(t.getIdTrabajador(), ahora, p.getIntervaloAntirreboteSeg())) {
            log.debug("Escaneo descartado por anti-rebote: trabajador {}", t.getIdTrabajador());
            return respuestaSimple(t, "IGNORADO", ahora,
                    "Marcacion ya registrada hace unos segundos.");
        }

        // ---- 3. Confirmacion pendiente de entrada anticipada (HU-22) ----
        LectorEstadoService.Pendiente pend =
                lector.consumirPendiente(t.getIdTrabajador(), ahora);
        if (pend != null) {
            return confirmarEntradaAnticipada(t, pend, ahora, p);
        }

        // ---- 4. Resolver la jornada por ventana ----
        List<Asistencia> abiertas =
                asistenciaRepo.findJornadasAbiertasEnVentana(t.getIdTrabajador(), ahora);
        if (!abiertas.isEmpty()) {
            return procesarSalida(t, abiertas.get(0), ahora, p);
        }

        List<Asistencia> pendientes =
                asistenciaRepo.findJornadasPendientesEnVentana(t.getIdTrabajador(), ahora);
        if (!pendientes.isEmpty()) {
            return procesarEntrada(t, pendientes.get(0), ahora, p);
        }

        List<Asistencia> completas =
                asistenciaRepo.findJornadasCompletasEnVentana(t.getIdTrabajador(), ahora);
        if (!completas.isEmpty()) {
            return registrarAdicional(t, completas.get(0), ahora);
        }

        // ---- 5. Sin jornada programada (RN-23, EX3) ----
        throw new BusinessException(
                "No tienes horario programado en este momento. Contacta a tu jefe.");
    }

    // ---------- ENTRADA ----------

    private MarcarAsistenciaResponse procesarEntrada(Trabajador t, Asistencia a,
                                                     LocalDateTime ahora,
                                                     ParametrosGeneralesAsistencia p) {

        EsquemaHorario e = a.getEsquema();
        int tolPrevia   = e != null ? e.getToleranciaPrevia()   : 0;
        int tolTardanza = e != null ? e.getToleranciaTardanza() : 0;

        LocalDateTime ingresoProg = a.getIngresoProg();
        long anticipacion = ingresoProg != null && ahora.isBefore(ingresoProg)
                ? Duration.between(ahora, ingresoProg).toMinutes() : 0;

        // CASO B: anticipada mas alla de la tolerancia, dentro de P1.
        // Se abre la confirmacion y NO se guarda nada todavia.
        if (anticipacion > tolPrevia && anticipacion <= p.getMaxAnticipacionEntrada()) {
            lector.abrirConfirmacion(t.getIdTrabajador(), a.getIdAsistencia(),
                    ahora, p.getVentanaConfirmacionSeg());
            lector.registrarEscaneo(t.getIdTrabajador(), ahora);

            return MarcarAsistenciaResponse.builder()
                    .idTrabajador(t.getIdTrabajador())
                    .nombreCompleto(t.getNombreCompleto())
                    .accion("CONFIRMACION_REQUERIDA")
                    .hora(ahora.format(HHMM))
                    .tipo(a.getTipo().name())
                    .estado(a.getEstado().name())
                    .puestoNombre(nombrePuesto(t))
                    .ingresoProg(ingresoProg != null ? ingresoProg.format(HHMM) : null)
                    .requiereConfirmacion(true)
                    .segundosParaConfirmar(p.getVentanaConfirmacionSeg())
                    .mensaje("Ingreso anticipado. Realizaras horas extra? "
                            + "Vuelve a pasar el codigo para confirmar.")
                    .build();
        }

        // CASO C: mas alla de P1. Se deriva a registro manual (CU19).
        if (anticipacion > p.getMaxAnticipacionEntrada()) {
            throw new BusinessException(
                    "Marcacion fuera de rango. Contacta a tu administrador.");
        }

        // CASO A: dentro de tolerancia. Se registra normalmente.
        a.setIngresoReal(ahora);
        a.setEstado(EstadoAsistencia.MARCADO);

        // Vino a trabajar pese a tener una ausencia registrada.
        //
        // NO se rechaza la marcacion: el trabajador esta fisicamente en
        // planta y el hecho debe quedar registrado. Pero se marca para
        // revision, porque hay una contradiccion que solo el Jefe puede
        // resolver: o el permiso no se uso, o alguien marco por el.
        //
        // El consolidado ya da prioridad a la jornada real sobre la
        // ausencia declarada, asi que las horas se pagan igual.
        if (a.tieneAusenciaJustificada() && !a.isEsDiaNoLaborable()) {
            a.setRequiereRevision(true);
            a.setObservacion(concatenar(a.getObservacion(),
                    "Marco entrada teniendo "
                            + (a.getPermiso() != null ? "permiso" : "falta justificada")
                            + " registrado para esta fecha."));
        }

        int tardanza = 0;
        if (ingresoProg != null && ahora.isAfter(ingresoProg)) {
            tardanza = (int) Duration.between(ingresoProg, ahora).toMinutes();
        }
        a.setMinTardanza(tardanza);
        // La tardanza es un hecho calculado, no una decision pendiente:
        // no activa el indicador de revision (HU-21, criterio 2).

        asistenciaRepo.save(a);
        lector.registrarEscaneo(t.getIdTrabajador(), ahora);

        String mensaje = tardanza > tolTardanza
                ? "Entrada registrada. Tardanza de " + tardanza + " minutos."
                : "Entrada registrada.";

        return respuesta(t, a, "ENTRADA", ahora, mensaje, tardanza);
    }

    /**
     * Segundo escaneo dentro de la ventana: confirma la entrada
     * anticipada.
     *
     * Se registra el instante del PRIMER escaneo, no el del segundo: el
     * trabajador llego cuando paso su carne la primera vez.
     */
    private MarcarAsistenciaResponse confirmarEntradaAnticipada(
            Trabajador t, LectorEstadoService.Pendiente pend,
            LocalDateTime ahora, ParametrosGeneralesAsistencia p) {

        Asistencia a = asistenciaRepo.findById(pend.idAsistencia())
                .orElseThrow(() -> new ResourceNotFoundException("Jornada no encontrada."));

        a.setIngresoReal(pend.instanteEscaneo());
        a.setTipo(TipoRegistro.HORA_EXTRA_NO_PROGRAMADA);
        a.setEstado(EstadoAsistencia.MARCADO);
        a.setRequiereRevision(true);
        a.setMinTardanza(0);

        if (a.getIngresoProg() != null) {
            a.setMinPrevIngProg((int) Duration.between(
                    pend.instanteEscaneo(), a.getIngresoProg()).toMinutes());
        }

        asistenciaRepo.save(a);
        lector.registrarEscaneo(t.getIdTrabajador(), ahora);

        return respuesta(t, a, "ENTRADA", pend.instanteEscaneo(),
                "Entrada registrada, sujeta a revision de tu jefe.", 0);
    }

    // ---------- SALIDA ----------

    private MarcarAsistenciaResponse procesarSalida(Trabajador t, Asistencia a,
                                                    LocalDateTime ahora,
                                                    ParametrosGeneralesAsistencia p) {

        EsquemaHorario e = a.getEsquema();
        int tolPosterior = e != null ? e.getToleranciaPosterior() : 0;

        // La salida SIEMPRE se registra (RN-43).
        a.setSalidaReal(ahora);
        a.recalcularTiempos();
        a.setMinutosFeriado(feriadoService.calcularMinutosFeriado(a));
        a.setEstado(EstadoAsistencia.CALCULADO);

        long exceso = a.getSalidaProg() != null && ahora.isAfter(a.getSalidaProg())
                ? Duration.between(a.getSalidaProg(), ahora).toMinutes() : 0;
        int anticipadoValidado = a.getValMinPrevIng() != null ? a.getValMinPrevIng() : 0;
        int anticipadoReal     = a.getMinPrevIngProg() != null ? a.getMinPrevIngProg() : 0;
        long combinado = exceso + Math.max(anticipadoValidado, anticipadoReal);

        String mensaje;

        if (exceso <= tolPosterior) {
            // CASO A: la salida esta dentro de tolerancia.
            //
            // OJO: esto NO significa que la jornada no requiera revision.
            // La entrada pudo dejar algo pendiente: una entrada anticipada
            // confirmada por doble escaneo (HORA_EXTRA_NO_PROGRAMADA), o
            // una marcacion sobre un dia con ausencia registrada.
            //
            // La version anterior hacia setRequiereRevision(false) sin
            // condicion, de modo que una salida puntual BORRABA el
            // pendiente marcado en la entrada. La hora extra confirmada
            // desaparecia de la bandeja sin que nadie la viera y, como
            // RN-33 excluye del consolidado la hora extra no validada, el
            // trabajador la perdia en silencio.
            //
            // El indicador solo lo levanta quien resuelve: el Jefe al
            // validar (CU18), o el cierre diario al cubrirla con una
            // ausencia. Aqui solo se anade, nunca se quita.
            if (a.isRequiereRevision()) {
                a.setEstado(EstadoAsistencia.CALCULADO);
                mensaje = "Salida registrada. Tu jefe revisara el tiempo adicional.";
            } else {
                a.setEstado(EstadoAsistencia.REVISADO);
                a.setRevisadoEn(ahora);
                mensaje = "Salida registrada.";
            }

        } else if (exceso <= p.getMaxExcesoSalida() && combinado <= p.getTopeCombinado()) {
            // CASO B: excedente dentro de los parametros. Pendiente de
            // validar como hora extra excepcional (CU18).
            a.setRequiereRevision(true);
            mensaje = "Salida registrada. El tiempo adicional pasara a revision de tu jefe.";

        } else {
            // CASO C: fuera de P2 o de P3. Se registra igual y se marca
            // para revision obligatoria. No se acepta automaticamente
            // como jornada normal (RN-27) pero tampoco se pierde el dato.
            a.setRequiereRevision(true);
            a.setObservacion(concatenar(a.getObservacion(),
                    "Excede los parametros configurados (exceso " + exceso
                            + " min, combinado " + combinado + " min)."));
            mensaje = "Salida registrada. Tu jefe revisara el tiempo adicional.";
        }

        asistenciaRepo.save(a);
        lector.registrarEscaneo(t.getIdTrabajador(), ahora);

        return respuesta(t, a, "SALIDA", ahora, mensaje, a.getMinTardanza());
    }

    // ---------- MARCACION ADICIONAL ----------

    /**
     * Marcacion sobre una jornada ya completa (posible doblete).
     *
     * Se conserva como una fila nueva de tipo NO_PROGRAMADA para que el
     * Jefe decida (RN-26). El sistema no intenta interpretarla.
     *
     * Esto era imposible en el prototipo: la constraint
     * UNIQUE (id_trabajador, fecha, tipo) impedia una segunda fila del
     * mismo tipo el mismo dia.
     */
    private MarcarAsistenciaResponse registrarAdicional(Trabajador t, Asistencia previa,
                                                        LocalDateTime ahora) {
        Quincena q = preRegistroService.resolverOCrear(ahora);

        Asistencia nueva = Asistencia.builder()
                .trabajador(t)
                .fecha(ahora.toLocalDate())
                .tipo(TipoRegistro.NO_PROGRAMADA)
                .estado(EstadoAsistencia.MARCADO)
                .requiereRevision(true)
                .turno(previa.getTurno())
                .quincena(q)
                .ingresoReal(ahora)
                .inicioVentana(ahora)
                .finVentana(ahora.plusHours(24))
                .fechaRegistro(ahora)
                .observacion("Marcacion adicional sobre una jornada ya completa. "
                        + "Requiere resolucion del jefe.")
                .build();

        asistenciaRepo.save(nueva);
        lector.registrarEscaneo(t.getIdTrabajador(), ahora);

        return respuesta(t, nueva, "ENTRADA", ahora,
                "Marcacion registrada como no programada. Tu jefe la revisara.", 0);
    }

    // ════════════════════════════════════════════════════════════
    // PANEL DEL DIA (CU06)
    // ════════════════════════════════════════════════════════════

    @Override
    public List<AsistenciaResumenDTO> getTrabajadoresEnPlanta() {
        return asistenciaRepo.findEnPlanta(LocalDateTime.now())
                .stream().map(this::toResumenDTO).collect(Collectors.toList());
    }

    @Override
    public List<EnPlantaPublicDTO> getEnPlantaPublica() {
        return asistenciaRepo.findEnPlanta(LocalDateTime.now())
                .stream().map(a -> {
                    Trabajador t = a.getTrabajador();
                    return EnPlantaPublicDTO.builder()
                            .nombreCompleto(t.getNombreCompleto())
                            .puestoNombre(nombrePuesto(t))
                            .areaNombre(nombreArea(t))
                            .horaEntrada(a.getIngresoReal() != null
                                    ? a.getIngresoReal().format(HHMM) : null)
                            .turnoNombre(a.getTurno() != null ? a.getTurno().getNombre() : null)
                            .build();
                }).collect(Collectors.toList());
    }

    /**
     * Jornadas cuya ventana toca el dia de hoy.
     *
     * Ya no filtra por fecha exacta: con dos turnos en paralelo, la
     * jornada nocturna iniciada anoche sigue en curso y su gente esta en
     * planta ahora mismo.
     */
    @Override
    public List<AsistenciaResumenDTO> getAsistenciasDia() {
        LocalDate hoy = LocalDate.now();
        return asistenciaRepo
                .findPorIntervalo(hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay())
                .stream().map(this::toResumenDTO).collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════
    // BANDEJA DE PENDIENTES (CU20)
    // ════════════════════════════════════════════════════════════

    @Override
    public List<AsistenciaRevisionDTO> getParaRevision(Long idQuincena) {
        List<Asistencia> asistencias = asistenciaRepo.findByQuincena(idQuincena);

        // El Jefe solo ve su area
        if (securityHelper.esJefe()) {
            Integer idArea = securityHelper.getIdAreaJefeAutenticado();
            if (idArea == null) return List.of();
            asistencias = asistencias.stream()
                    .filter(a -> a.getTrabajador().getArea() != null
                            && idArea.equals(a.getTrabajador().getArea().getIdArea()))
                    .collect(Collectors.toList());
        }

        return asistencias.stream().map(this::toRevisionDTO).collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════
    // VALIDAR HORA EXTRA (CU18)
    // ════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public AsistenciaRevisionDTO validarTiempos(ValidarTiemposRequest req,
                                                String usernameRevisor) {
        Asistencia a = asistenciaRepo.findById(req.getIdAsistencia())
                .orElseThrow(() -> new ResourceNotFoundException("Asistencia no encontrada."));

        verificarSegregacion(a.getTrabajador());

        if (a.getEstado() == EstadoAsistencia.CONSOLIDADO)
            throw new BusinessException(
                    "Esta asistencia ya fue consolidada y no puede modificarse.");

        if (a.getQuincena() != null && a.getQuincena().getEstado() == EstadoQuincena.CERRADA)
            throw new BusinessException(
                    "La quincena esta cerrada. Debe reabrirse antes de modificar sus registros.");

        // Comentario obligatorio (RN-02). El prototipo no lo exigia
        // realmente: el campo carecia de validacion.
        if (req.getObservacion() == null || req.getObservacion().isBlank())
            throw new BusinessException("El motivo o comentario es obligatorio.");

        int maxPrev = nz(a.getMinPrevIngProg());
        int maxPost = nz(a.getMinPostSalProg());

        if (req.getValMinPrevIng() != null && req.getValMinPrevIng() > maxPrev)
            throw new BusinessException("No puede validar mas minutos previos ("
                    + req.getValMinPrevIng() + ") de los existentes (" + maxPrev + ").");

        if (req.getValMinPostSal() != null && req.getValMinPostSal() > maxPost)
            throw new BusinessException("No puede validar mas minutos posteriores ("
                    + req.getValMinPostSal() + ") de los existentes (" + maxPost + ").");

        String antes = "prev=" + a.getValMinPrevIng() + " post=" + a.getValMinPostSal();

        a.setValMinPrevIng(nz(req.getValMinPrevIng()));
        a.setValMinPostSal(nz(req.getValMinPostSal()));
        a.setObservacion(req.getObservacion());

        if (req.getResultado() != null && !req.getResultado().isBlank())
            a.setResultadoValidacion(ResultadoValidacion.valueOf(req.getResultado()));

        if (req.getIdTurno() != null && a.getTurno() == null) {
            // El turno de una jornada sin esquema lo asigna manualmente
            // quien la resuelve; no se infiere de la hora (RN-25).
            a.setTurno(new Turno());
            a.getTurno().setIdTurno(req.getIdTurno());
        }

        if (a.isCompleta()) {
            a.recalcularTiempos();
            a.setMinutosFeriado(feriadoService.calcularMinutosFeriado(a));
        }

        Usuario revisor = usuarioRepo.findByUsername(usernameRevisor).orElse(null);
        a.setRevisadoPor(revisor);
        a.setRevisadoEn(LocalDateTime.now());
        a.setEstado(EstadoAsistencia.REVISADO);
        a.setRequiereRevision(false);

        Asistencia guardada = asistenciaRepo.save(a);

        auditoria.registrarCampo(TABLA, a.getIdAsistencia(), "VALIDAR_TIEMPOS",
                "validacion", antes,
                "prev=" + a.getValMinPrevIng() + " post=" + a.getValMinPostSal());

        return toRevisionDTO(guardada);
    }

    // ════════════════════════════════════════════════════════════
    // CORREGIR MARCACION (CU15)
    // ════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public AsistenciaRevisionDTO corregirMarcacion(CorregirMarcacionRequest req) {
        Asistencia a = asistenciaRepo.findById(req.getIdAsistencia())
                .orElseThrow(() -> new ResourceNotFoundException("Asistencia no encontrada."));

        verificarSegregacion(a.getTrabajador());

        if (a.getEstado() == EstadoAsistencia.CONSOLIDADO)
            throw new BusinessException("Esta asistencia ya fue consolidada.");

        if (req.getMotivo() == null || req.getMotivo().isBlank())
            throw new BusinessException("El motivo es obligatorio.");

        String antes = fmt(a.getIngresoReal()) + " a " + fmt(a.getSalidaReal());

        // Truncado al minuto, igual que en marcar(): el formato ISO admite
        // segundos y mezclarlos con marcaciones truncadas produciria
        // diferencias de un minuto en el calculo de horas.
        if (req.getIngresoReal() != null && !req.getIngresoReal().isBlank())
            a.setIngresoReal(LocalDateTime.parse(req.getIngresoReal()).withSecond(0).withNano(0));
        if (req.getSalidaReal() != null && !req.getSalidaReal().isBlank())
            a.setSalidaReal(LocalDateTime.parse(req.getSalidaReal()).withSecond(0).withNano(0));

        if (a.isCompleta()) {
            a.recalcularTiempos();
            a.setMinutosFeriado(feriadoService.calcularMinutosFeriado(a));
            if (a.getTipo() == TipoRegistro.MARCACION_INCOMPLETA)
                a.setTipo(TipoRegistro.PROGRAMADA);
        }

        a.setObservacion(req.getMotivo());
        a.setRevisadoPor(securityHelper.getUsuarioAutenticado());
        a.setRevisadoEn(LocalDateTime.now());
        a.setEstado(EstadoAsistencia.REVISADO);
        a.setRequiereRevision(false);

        Asistencia guardada = asistenciaRepo.save(a);

        auditoria.registrarCampo(TABLA, a.getIdAsistencia(), "CORREGIR_MARCACION",
                "marcacion", antes, fmt(a.getIngresoReal()) + " a " + fmt(a.getSalidaReal()));

        return toRevisionDTO(guardada);
    }

    // ════════════════════════════════════════════════════════════
    // REGISTRO POR CONTINGENCIA (CU19)
    // ════════════════════════════════════════════════════════════

    /**
     * Admite registrar solo la entrada, solo la salida, o ambas.
     *
     * El prototipo exigia ambos extremos de forma simultanea, lo que
     * impedia cubrir la contingencia con dato parcial conocido, que es el
     * caso mas frecuente cuando falla el lector a media jornada.
     */
    @Override
    @Transactional
    public AsistenciaRevisionDTO registrarNoProgramada(RegistrarNoProgramadaRequest req) {
        if (req.getObservacion() == null || req.getObservacion().isBlank())
            throw new BusinessException("El motivo es obligatorio.");

        if ((req.getIngresoReal() == null || req.getIngresoReal().isBlank())
                && (req.getSalidaReal() == null || req.getSalidaReal().isBlank()))
            throw new BusinessException("Debe indicar al menos la entrada o la salida.");

        Trabajador t = trabajadorRepo.findById(req.getIdTrabajador())
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado."));

        verificarSegregacion(t);

        LocalDate fecha = LocalDate.parse(req.getFecha());

        LocalDateTime ingreso = (req.getIngresoReal() != null && !req.getIngresoReal().isBlank())
                ? fecha.atTime(java.time.LocalTime.parse(req.getIngresoReal())) : null;

        LocalDateTime salida = null;
        if (req.getSalidaReal() != null && !req.getSalidaReal().isBlank()) {
            salida = fecha.atTime(java.time.LocalTime.parse(req.getSalidaReal()));
            // Si la salida es anterior a la entrada, la jornada cruzo la
            // medianoche y termina al dia siguiente.
            if (ingreso != null && !salida.isAfter(ingreso)) salida = salida.plusDays(1);
        }

        LocalDateTime referencia = ingreso != null ? ingreso : salida;
        Quincena q = preRegistroService.resolverOCrear(referencia);

        if (q.getEstado() == EstadoQuincena.CERRADA)
            throw new BusinessException(
                    "La quincena " + q.getDescripcion() + " esta cerrada. "
                            + "Debe reabrirse para registrar en ella.");

        Asistencia a = Asistencia.builder()
                .trabajador(t)
                .fecha(fecha)
                .tipo(TipoRegistro.CONTINGENCIA)
                .estado(ingreso != null && salida != null
                        ? EstadoAsistencia.CALCULADO : EstadoAsistencia.MARCADO)
                .requiereRevision(ingreso == null || salida == null)
                .quincena(q)
                .ingresoReal(ingreso)
                .salidaReal(salida)
                .inicioVentana(referencia.minusHours(1))
                .finVentana(referencia.plusHours(24))
                .esDiaNoLaborable(feriadoService.esFeriado(fecha))
                .registradoPor(securityHelper.getUsuarioAutenticado())
                .fechaRegistro(LocalDateTime.now())
                .observacion(req.getObservacion())
                .build();

        if (req.getIdTurno() != null) {
            Turno turno = new Turno();
            turno.setIdTurno(req.getIdTurno());
            a.setTurno(turno);
        }

        if (a.isCompleta()) {
            a.recalcularTiempos();
            a.setMinutosFeriado(feriadoService.calcularMinutosFeriado(a));
        }

        Asistencia guardada = asistenciaRepo.save(a);

        auditoria.registrarCampo(TABLA, guardada.getIdAsistencia(), "CONTINGENCIA",
                "registro_manual", null,
                fecha + " " + fmt(ingreso) + " a " + fmt(salida)
                        + " | motivo: " + req.getObservacion());

        return toRevisionDTO(guardada);
    }

    // ════════════════════════════════════════════════════════════
    // QUINCENAS
    // ════════════════════════════════════════════════════════════

    @Override
    public List<AsistenciaDTOs.QuincenaResumenDTO> getQuincenas() {
        return quincenaRepo.findAllByOrderByInicioDesc()
                .stream().map(this::toQuincenaDTO).collect(Collectors.toList());
    }

    // El metodo crearQuincena() y su endpoint desaparecen. La quincena se
    // autogenera al confirmar la programacion semanal (RN-35, CU14).

    // ════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════

    /** Segregacion de funciones (RN-01). Validada en el servidor. */
    private void verificarSegregacion(Trabajador afectado) {
        int nivelActor = nivelDe(securityHelper.getRol());
        if (nivelActor == 5) return;   // Superadministrador, unica excepcion

        Usuario actor = securityHelper.getUsuarioAutenticado();
        Long idActor = actor.getTrabajador() != null
                ? actor.getTrabajador().getIdTrabajador() : null;

        if (afectado.getIdTrabajador().equals(idActor))
            throw new BusinessException(
                    "No puedes resolver tu propio registro. "
                            + "La accion corresponde al nivel inmediato superior.");

        int nivelAfectado = nivelDe(afectado.getUsuario() != null
                ? afectado.getUsuario().getRol() : "ROLE_TRABAJADOR");

        if (nivelActor <= nivelAfectado)
            throw new BusinessException(
                    "No puedes resolver el registro de alguien de tu mismo nivel jerarquico "
                            + "o superior. La accion corresponde al nivel inmediato superior.");
    }

    private int nivelDe(String rol) {
        if (rol == null) return 1;
        return switch (rol) {
            case "ROLE_SUPERADMIN" -> 5;
            case "ROLE_ADMIN"      -> 4;
            case "ROLE_JEFE"       -> 3;
            case "ROLE_SUPERVISOR" -> 2;
            default                -> 1;
        };
    }

    private MarcarAsistenciaResponse respuesta(Trabajador t, Asistencia a, String accion,
                                               LocalDateTime instante, String mensaje,
                                               Integer tardanza) {
        return MarcarAsistenciaResponse.builder()
                .idTrabajador(t.getIdTrabajador())
                .nombreCompleto(t.getNombreCompleto())
                .accion(accion)
                .hora(instante.format(HHMM))
                .estado(a.getEstado().name())
                .tipo(a.getTipo().name())
                .puestoNombre(nombrePuesto(t))
                .turnoNombre(a.getTurno() != null ? a.getTurno().getNombre() : null)
                .ingresoProg(a.getIngresoProg() != null ? a.getIngresoProg().format(HHMM) : null)
                .salidaProg(a.getSalidaProg() != null ? a.getSalidaProg().format(HHMM) : null)
                .minTardanza(tardanza)
                .requiereRevision(a.isRequiereRevision())
                .requiereConfirmacion(false)
                .mensaje(mensaje)
                .build();
    }

    private MarcarAsistenciaResponse respuestaSimple(Trabajador t, String accion,
                                                     LocalDateTime instante, String mensaje) {
        return MarcarAsistenciaResponse.builder()
                .idTrabajador(t.getIdTrabajador())
                .nombreCompleto(t.getNombreCompleto())
                .accion(accion)
                .hora(instante.format(HHMM))
                .puestoNombre(nombrePuesto(t))
                .requiereConfirmacion(false)
                .mensaje(mensaje)
                .build();
    }

    private AsistenciaResumenDTO toResumenDTO(Asistencia a) {
        Trabajador t = a.getTrabajador();
        return AsistenciaResumenDTO.builder()
                .idAsistencia(a.getIdAsistencia())
                .idTrabajador(t.getIdTrabajador())
                .nombreCompleto(t.getNombreCompleto())
                .nroDocumento(t.getNroDocumento())
                .puestoNombre(nombrePuesto(t))
                .areaNombre(nombreArea(t))
                .fecha(a.getFecha().toString())
                .horaEntrada(fmt(a.getIngresoReal()))
                .horaSalida(fmt(a.getSalidaReal()))
                .estado(a.getEstado().name())
                .tipo(a.getTipo().name())
                .turnoNombre(a.getTurno() != null ? a.getTurno().getNombre() : null)
                .requiereRevision(a.isRequiereRevision())
                .minTardanza(a.getMinTardanza())
                .permisoAsociado(a.getPermiso() != null
                        ? a.getPermiso().getTipoAusencia().getNombre() : null)
                .faltaJustificadaAsociada(a.getFaltaJustificada() != null
                        ? a.getFaltaJustificada().getTipoAusencia().getNombre() : null)
                .minHorasTotales(a.getMinHorasTotales())
                .minutosFeriado(a.getMinutosFeriado())
                .esDiaNoLaborable(a.isEsDiaNoLaborable())
                .build();
    }

    private AsistenciaRevisionDTO toRevisionDTO(Asistencia a) {
        Trabajador t = a.getTrabajador();
        return AsistenciaRevisionDTO.builder()
                .idAsistencia(a.getIdAsistencia())
                .idTrabajador(t.getIdTrabajador())
                .nombreCompleto(t.getNombreCompleto())
                .nroDocumento(t.getNroDocumento())
                .puestoNombre(nombrePuesto(t))
                .areaNombre(nombreArea(t))
                .fecha(a.getFecha().toString())
                .tipo(a.getTipo().name())
                .estado(a.getEstado().name())
                .requiereRevision(a.isRequiereRevision())
                .turnoNombre(a.getTurno() != null ? a.getTurno().getNombre() : null)
                .esDiaNoLaborable(a.isEsDiaNoLaborable())
                .ingresoProg(fmt(a.getIngresoProg()))
                .salidaProg(fmt(a.getSalidaProg()))
                .minRefrigerioProg(a.getMinRefrigerioProg())
                .minNetosProg(a.getMinNetosProg())
                .minExtraProg(a.getMinExtraProg())
                .ingresoReal(fmt(a.getIngresoReal()))
                .salidaReal(fmt(a.getSalidaReal()))
                .minPrevIngProg(a.getMinPrevIngProg())
                .minPostSalProg(a.getMinPostSalProg())
                .minTardanza(a.getMinTardanza())
                .minSalTemprana(a.getMinSalTemprana())
                .minHorasTotales(a.getMinHorasTotales())
                .minutosFeriado(a.getMinutosFeriado())
                .valMinPrevIng(a.getValMinPrevIng())
                .valMinPostSal(a.getValMinPostSal())
                .resultadoValidacion(a.getResultadoValidacion() != null
                        ? a.getResultadoValidacion().name() : null)
                .permisoAsociado(a.getPermiso() != null
                        ? a.getPermiso().getTipoAusencia().getNombre() : null)
                .faltaJustificadaAsociada(a.getFaltaJustificada() != null
                        ? a.getFaltaJustificada().getTipoAusencia().getNombre() : null)
                .revisadoPor(a.getRevisadoPor() != null ? a.getRevisadoPor().getUsername() : null)
                .revisadoEn(a.getRevisadoEn() != null ? a.getRevisadoEn().toString() : null)
                .observacion(a.getObservacion())
                .colorPrev(colorIndicador(a.getMinPrevIngProg()))
                .colorPost(colorIndicador(a.getMinPostSalProg()))
                .build();
    }

    private AsistenciaDTOs.QuincenaResumenDTO toQuincenaDTO(Quincena q) {
        return AsistenciaDTOs.QuincenaResumenDTO.builder()
                .idQuincena(q.getIdQuincena())
                .descripcion(q.getDescripcion())
                .inicio(q.getInicio().toString())
                .fin(q.getFin().toString())
                .estado(q.getEstado().name())
                .bloqueantes(asistenciaRepo.countBloqueantes(q.getIdQuincena()))
                .build();
    }

    private String colorIndicador(Integer minutos) {
        if (minutos == null || minutos == 0) return "gris";
        if (minutos < 10) return "gris";
        if (minutos < 30) return "amarillo-palido";
        if (minutos < 60) return "amarillo";
        return "naranja";
    }

    private String nombrePuesto(Trabajador t) {
        return t.getPuesto() != null ? t.getPuesto().getPuesto() : null;
    }

    private String nombreArea(Trabajador t) {
        return t.getArea() != null ? t.getArea().getArea() : null;
    }

    private String fmt(LocalDateTime dt) {
        return dt != null ? dt.format(HHMM) : null;
    }

    private String concatenar(String previo, String nuevo) {
        if (previo == null || previo.isBlank()) return nuevo;
        return previo + " | " + nuevo;
    }

    private int nz(Integer v) {
        return v != null ? v : 0;
    }
}
