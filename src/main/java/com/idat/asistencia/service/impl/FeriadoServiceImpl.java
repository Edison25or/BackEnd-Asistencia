package com.idat.asistencia.service.impl;

import com.idat.asistencia.dto.FeriadoDTOs.*;
import com.idat.asistencia.exception.BusinessException;
import com.idat.asistencia.exception.ResourceNotFoundException;
import com.idat.asistencia.model.entity.Asistencia;
import com.idat.asistencia.model.entity.Feriado;
import com.idat.asistencia.model.entity.ParametrosGeneralesAsistencia;
import com.idat.asistencia.model.enums.EstadoAsistencia;
import com.idat.asistencia.repository.AsistenciaRepository;
import com.idat.asistencia.repository.FeriadoRepository;
import com.idat.asistencia.security.SecurityHelper;
import com.idat.asistencia.service.AuditoriaService;
import com.idat.asistencia.service.FeriadoService;
import com.idat.asistencia.service.ParametrosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Registro de feriados y computo de horas trabajadas en ellos (RN-41).
 *
 * ============================================================
 * POR QUE EL COMPUTO ES POR DIA CALENDARIO
 * ============================================================
 * La planta opera dos turnos en paralelo y el turno noche cruza la
 * medianoche (RT-09). Con un feriado el sabado 28, turno dia de 06:00 a
 * 14:00 y turno noche de 22:00 a 06:00, las tres jornadas en juego son:
 *
 *   noche que entra el 27  ->  360 min dentro del 28
 *   dia   que entra el 28  ->  480 min dentro del 28
 *   noche que entra el 28  ->  120 min dentro del 28
 *
 * Ninguna regla que atribuya la jornada COMPLETA a un solo dia da el
 * resultado correcto para las tres a la vez. Por eso se cuentan minutos
 * de solapamiento y no jornadas.
 *
 * La bandera esDiaNoLaborable de Asistencia cumple una funcion distinta:
 * se ancla a la fecha de la jornada y sirve para que el cierre diario no
 * genere falta cuando nadie marco (RN-42).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeriadoServiceImpl implements FeriadoService {

    private final FeriadoRepository     feriadoRepo;
    private final AsistenciaRepository  asistenciaRepo;
    private final ParametrosService     parametrosService;
    private final SecurityHelper        securityHelper;
    private final AuditoriaService      auditoria;

    private static final String TABLA = "feriados";

    // ============================================================
    // COMPUTO DE MINUTOS
    // ============================================================

    /**
     * Minutos de una jornada que caen dentro de dias feriados.
     *
     * Una jornada nocturna puede tocar dos dias calendario, de modo que
     * se suman los solapamientos con todos los feriados del rango.
     */
    @Override
    public int calcularMinutosFeriado(Asistencia a) {
        if (a.getIngresoReal() == null || a.getSalidaReal() == null) return 0;

        LocalDate desde = a.getIngresoReal().toLocalDate();
        LocalDate hasta = a.getSalidaReal().toLocalDate();

        List<Feriado> feriados = feriadoRepo.findActivosEnRango(desde, hasta);
        if (feriados.isEmpty()) return 0;

        int total = 0;
        for (Feriado f : feriados) {
            total += minutosSolapados(
                    a.getIngresoReal(), a.getSalidaReal(),
                    f.getInicioDia(),   f.getFinDia());
        }

        // El total no puede exceder la duracion real de la jornada. La
        // guarda cubre el caso teorico de dos feriados solapados por un
        // dato mal cargado.
        int duracion = (int) Duration.between(a.getIngresoReal(), a.getSalidaReal()).toMinutes();
        total = Math.min(total, Math.max(0, duracion));

        // Descuento proporcional del refrigerio, si esta configurado.
        // Sin el, el total de minutos de feriado seria inconsistente con
        // el criterio de horas netas que usa el resto del consolidado.
        // Ver decision pendiente PD-03.
        ParametrosGeneralesAsistencia p = parametrosService.getGenerales();
        if (p.isDescontarRefrigerioFeriado()
                && a.getMinRefrigerioProg() != null
                && a.getMinRefrigerioProg() > 0
                && duracion > 0) {
            double proporcion = (double) total / duracion;
            total -= (int) Math.round(a.getMinRefrigerioProg() * proporcion);
        }

        return Math.max(0, total);
    }

    /** Minutos de interseccion entre dos intervalos. Cero si no se tocan. */
    private int minutosSolapados(LocalDateTime aIni, LocalDateTime aFin,
                                 LocalDateTime bIni, LocalDateTime bFin) {
        LocalDateTime ini = aIni.isAfter(bIni)  ? aIni : bIni;
        LocalDateTime fin = aFin.isBefore(bFin) ? aFin : bFin;
        if (!ini.isBefore(fin)) return 0;
        return (int) Duration.between(ini, fin).toMinutes();
    }

    @Override
    public boolean esFeriado(LocalDate fecha) {
        return feriadoRepo.existsByFechaAndActivoTrue(fecha);
    }

    // ============================================================
    // CATALOGO
    // ============================================================

    @Override
    public List<FeriadoResponse> listar() {
        return feriadoRepo.findByActivoTrueOrderByFechaDesc()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * Vista previa del impacto ANTES de confirmar el registro.
     *
     * Con dos turnos en paralelo, saber a que jornadas alcanza un feriado
     * no es evidente: la nocturna de la vispera aporta minutos aunque su
     * fecha sea el dia anterior. Mostrar el conteo evita que un error de
     * fecha pase inadvertido hasta que el trabajador reclame su pago.
     */
    @Override
    public ImpactoFeriadoResponse previsualizar(LocalDate fecha) {
        List<Asistencia> conMinutos = asistenciaRepo.findQueSolapanDia(
                fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay());

        List<Asistencia> sinMarcar = asistenciaRepo.findSinMarcarEnFecha(fecha);

        List<DetalleImpactoDTO> detalle = conMinutos.stream()
                .map(a -> DetalleImpactoDTO.builder()
                        .idAsistencia(a.getIdAsistencia())
                        .trabajador(a.getTrabajador().getNombreCompleto())
                        .fechaJornada(a.getFecha().toString())
                        .turno(a.getTurno() != null ? a.getTurno().getNombre() : "-")
                        .minutosEnFeriado(minutosSolapados(
                                a.getIngresoReal(), a.getSalidaReal(),
                                fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay()))
                        .build())
                .collect(Collectors.toList());

        return ImpactoFeriadoResponse.builder()
                .fecha(fecha.toString())
                .jornadasConMinutos(conMinutos.size())
                .preRegistrosSinMarcar(sinMarcar.size())
                .detalle(detalle)
                .build();
    }

    /**
     * Registra un feriado y aplica su efecto sobre los registros
     * existentes.
     *
     * Es el caso del decreto tardio: en Peru los feriados se declaran a
     * veces con pocos dias de anticipacion, cuando la programacion ya fue
     * confirmada y hasta trabajada. El alta dispara dos efectos:
     *
     *   1. Recalcula minutosFeriado de las jornadas que solapan la fecha.
     *   2. Marca como no laborables los pre-registros sin marcar de ese
     *      dia, para que el cierre diario no genere falta injustificada.
     *
     * Ambos alcanzan solo a quincenas abiertas. Lo consolidado no se
     * toca (RN-32).
     */
    @Override
    @Transactional
    public ImpactoFeriadoResponse registrar(FeriadoRequest req) {
        LocalDate fecha = LocalDate.parse(req.getFecha());

        if (feriadoRepo.existsByFechaAndActivoTrue(fecha))
            throw new BusinessException("Ya existe un feriado registrado para el " + fecha + ".");

        Feriado f = feriadoRepo.save(Feriado.builder()
                .fecha(fecha)
                .descripcion(req.getDescripcion())
                .activo(true)
                .registradoPor(securityHelper.getUsuarioAutenticado())
                .fechaRegistro(LocalDateTime.now())
                .build());

        ImpactoFeriadoResponse impacto = aplicarSobreRegistros(fecha);
        impacto.setIdFeriado(f.getIdFeriado());

        auditoria.registrarCampo(TABLA, f.getIdFeriado().longValue(), "CREAR",
                "fecha", null,
                fecha + " (" + req.getDescripcion() + ") - "
                        + impacto.getJornadasConMinutos() + " jornadas recalculadas, "
                        + impacto.getPreRegistrosSinMarcar() + " pre-registros marcados");

        return impacto;
    }

    /**
     * Desactiva un feriado y revierte su efecto: limpia los minutos y la
     * bandera de no laborable de los registros afectados.
     *
     * Sin este camino, un error de fecha obliga a corregir a mano decenas
     * de filas.
     */
    @Override
    @Transactional
    public void desactivar(Integer idFeriado) {
        Feriado f = feriadoRepo.findById(idFeriado)
                .orElseThrow(() -> new ResourceNotFoundException("Feriado no encontrado: " + idFeriado));

        LocalDate fecha = f.getFecha();
        f.setActivo(false);
        feriadoRepo.save(f);

        // Revertir el computo. Se recalcula en vez de poner cero, porque
        // la jornada podria solapar tambien con otro feriado vigente.
        for (Asistencia a : asistenciaRepo.findQueSolapanDia(
                fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay())) {
            if (a.getEstado() == EstadoAsistencia.CONSOLIDADO) continue;
            a.setMinutosFeriado(calcularMinutosFeriado(a));
            asistenciaRepo.save(a);
        }

        for (Asistencia a : asistenciaRepo.findSinMarcarEnFecha(fecha)) {
            a.setEsDiaNoLaborable(false);
            asistenciaRepo.save(a);
        }

        auditoria.registrarCampo(TABLA, idFeriado.longValue(), "DESHABILITAR",
                "activo", "true", "false");
    }

    /**
     * Aplica el efecto de un feriado sobre los registros existentes.
     * Se invoca al registrarlo y es idempotente, de modo que puede
     * repetirse sin duplicar efectos.
     */
    @Override
    @Transactional
    public ImpactoFeriadoResponse aplicarSobreRegistros(LocalDate fecha) {
        int recalculadas = 0;
        for (Asistencia a : asistenciaRepo.findQueSolapanDia(
                fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay())) {
            if (a.getEstado() == EstadoAsistencia.CONSOLIDADO) continue;
            a.setMinutosFeriado(calcularMinutosFeriado(a));
            asistenciaRepo.save(a);
            recalculadas++;
        }

        int marcados = 0;
        for (Asistencia a : asistenciaRepo.findSinMarcarEnFecha(fecha)) {
            a.setEsDiaNoLaborable(true);
            asistenciaRepo.save(a);
            marcados++;
        }

        log.info("Feriado {}: {} jornadas recalculadas, {} pre-registros marcados",
                fecha, recalculadas, marcados);

        return ImpactoFeriadoResponse.builder()
                .fecha(fecha.toString())
                .jornadasConMinutos(recalculadas)
                .preRegistrosSinMarcar(marcados)
                .detalle(List.of())
                .build();
    }

    private FeriadoResponse toResponse(Feriado f) {
        return FeriadoResponse.builder()
                .idFeriado(f.getIdFeriado())
                .fecha(f.getFecha().toString())
                .descripcion(f.getDescripcion())
                .activo(f.isActivo())
                .registradoPor(f.getRegistradoPor() != null
                        ? f.getRegistradoPor().getUsername() : null)
                .build();
    }
}
