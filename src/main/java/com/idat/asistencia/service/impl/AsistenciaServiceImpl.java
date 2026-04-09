package com.idat.asistencia.service.impl;

import com.idat.asistencia.dto.AsistenciaDTOs;
import com.idat.asistencia.dto.AsistenciaDTOs.*;
import com.idat.asistencia.exception.BusinessException;
import com.idat.asistencia.exception.ResourceNotFoundException;
import com.idat.asistencia.model.entity.*;
import com.idat.asistencia.model.enums.*;
import com.idat.asistencia.repository.*;
import com.idat.asistencia.service.AsistenciaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AsistenciaServiceImpl implements AsistenciaService {

    private final AsistenciaRepository          asistenciaRepo;
    private final TrabajadorRepository          trabajadorRepo;
    private final ProgramacionSemanalRepository programacionRepo;
    private final HorarioDiaRepository          horarioDiaRepo;
    private final QuincenaRepository            quincenaRepo;
    private final UsuarioRepository             usuarioRepo;

    // ── Hora límite para clasificar turno nocturno ─────────────
    private static final LocalTime NOCTURNO_INICIO = LocalTime.of(19, 0);
    private static final LocalTime NOCTURNO_FIN    = LocalTime.of(5, 0);

    // ════════════════════════════════════════════════════════════
    // MARCAR ENTRADA / SALIDA (endpoint público del lector)
    // ════════════════════════════════════════════════════════════
    @Override
    @Transactional
    public MarcarAsistenciaResponse marcar(String codigo) {
        if (codigo == null || codigo.length() < 3)
            throw new BusinessException("Código inválido.");

        String sufijo = codigo.substring(codigo.length() - 2).toUpperCase();
        String idStr  = codigo.substring(0, codigo.length() - 2);

        if (!sufijo.equals("IN") && !sufijo.equals("OU"))
            throw new BusinessException("Sufijo inválido. Use IN o OU.");

        Long idTrabajador;
        try { idTrabajador = Long.parseLong(idStr); }
        catch (NumberFormatException e) { throw new BusinessException("ID inválido."); }

        Trabajador trabajador = trabajadorRepo.findById(idTrabajador)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado."));

        if (trabajador.getEstado() != EstadoTrabajador.ACTIVO)
            throw new BusinessException("El trabajador no está activo.");

        LocalDate hoy   = LocalDate.now();
        LocalTime ahora = LocalTime.now();

        // Buscar asistencia existente del día (pre-registro o marcado previo)
        Asistencia asistencia = asistenciaRepo
                .findByTrabajador_IdTrabajadorAndFecha(idTrabajador, hoy)
                .orElse(null);

        // Buscar programación y datos del esquema del día
        Optional<ProgramacionSemanal> progOpt =
                programacionRepo.findEsquemaParaTrabajadorEnFecha(idTrabajador, hoy);

        HorarioDia horarioDia = null;
        EsquemaHorario esquema = null;
        boolean tieneProgramacion = progOpt.isPresent();

        if (tieneProgramacion) {
            esquema = progOpt.get().getEsquema();
            int diaSemana = hoy.getDayOfWeek().getValue();
            horarioDia = horarioDiaRepo
                    .findByEsquemaAndDia(esquema.getIdEsquema(), diaSemana)
                    .orElse(null);
        }

        // Determinar quincena
        Quincena quincena = quincenaRepo.findByFechaAproximada(hoy, ahora).orElse(null);

        String accion;
        Integer minTardanza = null;

        if (sufijo.equals("IN")) {
            if (asistencia != null && asistencia.getIngresoReal() != null)
                throw new BusinessException("Ya registró su entrada hoy " + trabajador.getPNombre() + ".");

            // Construir o actualizar asistencia
            if (asistencia == null) {
                // Determinar tipo: si no tiene programación → NO_PROGRAMADA
                TipoAsistencia tipo = tieneProgramacion
                        ? TipoAsistencia.PROGRAMADA
                        : TipoAsistencia.NO_PROGRAMADA;

                asistencia = Asistencia.builder()
                        .trabajador(trabajador)
                        .fecha(hoy)
                        .tipo(tipo)
                        .estado(EstadoAsistencia.PENDIENTE)
                        .esquema(esquema)
                        .quincena(quincena)
                        .build();
            }

            // Poblar datos programados desde el esquema (solo si tiene horario)
            if (horarioDia != null && !Boolean.TRUE.equals(horarioDia.getEsDescanso())) {
                asistencia.setIngresoProg(horarioDia.getHoraEntrada());
                asistencia.setMinRefrigerioProg(horarioDia.getMinutosRefrigerio());
                asistencia.setMinNetosProg(horarioDia.getMinutosNetos());
                asistencia.setMinExtraProg(horarioDia.getMinutosExtraProgramado());
                // Calcular salida programada
                if (horarioDia.getHoraSalidaCalculada() != null)
                    asistencia.setSalidaProg(horarioDia.getHoraSalidaCalculada());
                // Clasificar nocturno
                asistencia.setEsNocturno(esNocturno(horarioDia.getHoraEntrada()));
            }

            asistencia.setIngresoReal(ahora);
            asistencia.setEstado(EstadoAsistencia.MARCADO);

            // Calcular estado diario
            if (asistencia.getIngresoProg() != null) {
                // Tiene horario programado → evaluar tardanza
                int tolerancia = esquema != null ? esquema.getToleranciaMinutos() : 0;
                long diffMin = Duration.between(asistencia.getIngresoProg(), ahora).toMinutes();
                if (diffMin > tolerancia) {
                    asistencia.setEstadoDiario("TARDE");
                    minTardanza = (int) diffMin;
                } else {
                    asistencia.setEstadoDiario("A_TIEMPO");
                }
            } else {
                // Sin horario programado → NO_PROGRAMADO
                asistencia.setEstadoDiario("NO_PROGRAMADO");
            }

            asistenciaRepo.save(asistencia);
            accion = "ENTRADA";

        } else {
            // === SALIDA ===
            if (asistencia == null || asistencia.getIngresoReal() == null)
                throw new BusinessException("No hay registro de entrada hoy para " + trabajador.getPNombre() + ".");
            if (asistencia.getSalidaReal() != null)
                throw new BusinessException("Ya registró su salida hoy " + trabajador.getPNombre() + ".");

            asistencia.setSalidaReal(ahora);
            // Recalcular todos los tiempos automáticamente
            asistencia.recalcularTiempos();
            asistenciaRepo.save(asistencia);
            accion = "SALIDA";
        }

        return MarcarAsistenciaResponse.builder()
                .idTrabajador(idTrabajador)
                .nombreCompleto(trabajador.getPNombre() + " " + trabajador.getAPaterno())
                .accion(accion)
                .hora(ahora.toString().substring(0, 5))
                .estado(asistencia.getEstado().name())
                .estadoDiario(asistencia.getEstadoDiario())
                .tipo(asistencia.getTipo().name())
                .puestoNombre(trabajador.getPuesto() != null ? trabajador.getPuesto().getPuesto() : null)
                .ingresoProg(asistencia.getIngresoProg() != null
                        ? asistencia.getIngresoProg().toString().substring(0, 5) : null)
                .minTardanza(minTardanza)
                .build();
    }

    // ════════════════════════════════════════════════════════════
    // CONSULTAS DE PANEL (compatibilidad con lo existente)
    // ════════════════════════════════════════════════════════════
    @Override
    public List<AsistenciaResumenDTO> getTrabajadoresEnPlanta() {
        return asistenciaRepo.findTrabajadoresEnPlanta(LocalDate.now())
                .stream().map(this::toResumenDTO).collect(Collectors.toList());
    }

    /**
     * Versión PÚBLICA de en-planta para la pantalla de marcado (kiosco).
     * No expone IDs, documentos ni datos sensibles.
     * Endpoint público: no requiere autenticación.
     */
    @Override
    public List<EnPlantaPublicDTO> getEnPlantaPublica() {
        return asistenciaRepo.findTrabajadoresEnPlanta(LocalDate.now())
                .stream().map(a -> {
                    Trabajador t = a.getTrabajador();
                    return EnPlantaPublicDTO.builder()
                            .nombreCompleto(t.getPNombre() + " " + t.getAPaterno() + " " + t.getAMaterno())
                            .puestoNombre(t.getPuesto() != null ? t.getPuesto().getPuesto() : null)
                            .areaNombre(t.getPuesto() != null && t.getPuesto().getArea() != null
                                    ? t.getPuesto().getArea().getArea() : null)
                            .horaEntrada(a.getIngresoReal() != null
                                    ? a.getIngresoReal().toString().substring(0, 5) : null)
                            .build();
                }).collect(Collectors.toList());
    }

    @Override
    public List<AsistenciaResumenDTO> getAsistenciasDia() {
        return asistenciaRepo.findByFecha(LocalDate.now())
                .stream().map(this::toResumenDTO).collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════
    // REVISIÓN DE ASISTENCIAS
    // ════════════════════════════════════════════════════════════

    /** Lista todas las asistencias de una quincena para el formulario de revisión */
    @Override
    public List<AsistenciaRevisionDTO> getParaRevision(Long idQuincena) {
        return asistenciaRepo.findByQuincena(idQuincena)
                .stream().map(this::toRevisionDTO).collect(Collectors.toList());
    }

    /** Valida los tiempos no programados de una asistencia */
    @Override
    @Transactional
    public AsistenciaRevisionDTO validarTiempos(ValidarTiemposRequest req,
                                                String usernameRevisor) {
        Asistencia a = asistenciaRepo.findById(req.getIdAsistencia())
                .orElseThrow(() -> new ResourceNotFoundException("Asistencia no encontrada."));

        // No se puede revisar lo que ya está consolidado
        if (a.getEstado() == EstadoAsistencia.CONSOLIDADO)
            throw new BusinessException("Esta asistencia ya fue consolidada y no puede modificarse.");

        // Validar que los valores no superen los tiempos calculados
        int maxPrev = a.getMinPrevIngProg() != null ? a.getMinPrevIngProg() : 0;
        int maxPost = a.getMinPostSalProg() != null ? a.getMinPostSalProg() : 0;

        if (req.getValMinPrevIng() != null && req.getValMinPrevIng() > maxPrev)
            throw new BusinessException(
                    "No puede validar más minutos previos (" + req.getValMinPrevIng()
                            + ") de los existentes (" + maxPrev + ").");

        if (req.getValMinPostSal() != null && req.getValMinPostSal() > maxPost)
            throw new BusinessException(
                    "No puede validar más minutos posteriores (" + req.getValMinPostSal()
                            + ") de los existentes (" + maxPost + ").");

        a.setValMinPrevIng(req.getValMinPrevIng() != null ? req.getValMinPrevIng() : 0);
        a.setValMinPostSal(req.getValMinPostSal() != null ? req.getValMinPostSal() : 0);

        if (req.getObservacion() != null) a.setObservacion(req.getObservacion());

        // Actualizar tipo si se envía (ej: marcar como FALTA o PERMISO)
        if (req.getTipo() != null) a.setTipo(TipoAsistencia.valueOf(req.getTipo()));

        // Recalcular horas totales con los nuevos valores de validación
        if (a.getIngresoReal() != null && a.getSalidaReal() != null)
            a.recalcularTiempos();

        // Registrar revisión
        Usuario revisor = usuarioRepo.findByUsername(usernameRevisor).orElse(null);
        a.setRevisadoPor(revisor);
        a.setRevisadoEn(LocalDateTime.now());
        a.setEstado(EstadoAsistencia.REVISADO);

        return toRevisionDTO(asistenciaRepo.save(a));
    }

    /** Registra una asistencia no programada (trabajo extraordinario) */
    @Override
    @Transactional
    public AsistenciaRevisionDTO registrarNoProgramada(RegistrarNoProgramadaRequest req) {
        LocalDate fecha      = LocalDate.parse(req.getFecha());
        LocalTime ingresado  = LocalTime.parse(req.getIngresoReal());
        LocalTime salida     = req.getSalidaReal() != null
                ? LocalTime.parse(req.getSalidaReal()) : null;

        Trabajador trabajador = trabajadorRepo.findById(req.getIdTrabajador())
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado."));

        if (asistenciaRepo.existsByTrabajador_IdTrabajadorAndFechaAndTipo(
                req.getIdTrabajador(), fecha, TipoAsistencia.NO_PROGRAMADA))
            throw new BusinessException("Ya existe una asistencia no programada para ese día.");

        Quincena quincena = quincenaRepo.findByFechaAproximada(fecha, ingresado).orElse(null);

        Asistencia a = Asistencia.builder()
                .trabajador(trabajador)
                .fecha(fecha)
                .tipo(TipoAsistencia.NO_PROGRAMADA)
                .estado(EstadoAsistencia.MARCADO)
                .ingresoReal(ingresado)
                .salidaReal(salida)
                .quincena(quincena)
                .esNocturno(esNocturno(ingresado))
                .observacion(req.getObservacion())
                .build();

        if (salida != null) a.recalcularTiempos();

        return toRevisionDTO(asistenciaRepo.save(a));
    }

    // ════════════════════════════════════════════════════════════
    // GENERACIÓN DE PRE-REGISTROS (se llama al confirmar semana)
    // ════════════════════════════════════════════════════════════
    @Transactional
    public void generarPreRegistros(Long idQuincena,
                                    List<ProgramacionSemanal> programaciones) {
        Quincena quincena = quincenaRepo.findById(idQuincena)
                .orElseThrow(() -> new ResourceNotFoundException("Quincena no encontrada."));

        for (ProgramacionSemanal prog : programaciones) {
            Trabajador t  = prog.getTrabajador();
            // Determinar los días del esquema dentro del período
            LocalDate inicio = prog.getSemanaInicio();
            LocalDate fin    = prog.getSemanaFin();

            // Iterar por cada día de la semana programada
            for (LocalDate dia = inicio; !dia.isAfter(fin); dia = dia.plusDays(1)) {
                int diaSemana = dia.getDayOfWeek().getValue();
                HorarioDia hd = horarioDiaRepo
                        .findByEsquemaAndDia(prog.getEsquema().getIdEsquema(), diaSemana)
                        .orElse(null);

                // No crear pre-registro para días de descanso
                if (hd == null || Boolean.TRUE.equals(hd.getEsDescanso())) continue;

                // No duplicar
                if (asistenciaRepo.existsByTrabajador_IdTrabajadorAndFechaAndTipo(
                        t.getIdTrabajador(), dia, TipoAsistencia.PROGRAMADA)) continue;

                Asistencia preReg = Asistencia.builder()
                        .trabajador(t)
                        .fecha(dia)
                        .tipo(TipoAsistencia.PROGRAMADA)
                        .estado(EstadoAsistencia.PENDIENTE)
                        .esquema(prog.getEsquema())
                        .programacion(prog)
                        .quincena(quincena)
                        .esNocturno(esNocturno(hd.getHoraEntrada()))
                        .ingresoProg(hd.getHoraEntrada())
                        .salidaProg(hd.getHoraSalidaCalculada())
                        .minRefrigerioProg(hd.getMinutosRefrigerio())
                        .minNetosProg(hd.getMinutosNetos())
                        .minExtraProg(hd.getMinutosExtraProgramado())
                        .build();

                asistenciaRepo.save(preReg);
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    // GESTIÓN DE QUINCENAS
    // ════════════════════════════════════════════════════════════
    @Override
    public List<AsistenciaDTOs.QuincenaResumenDTO> getQuincenas() {
        return quincenaRepo.findAll().stream()
                .sorted((a, b) -> {
                    int c = b.getAnio().compareTo(a.getAnio());
                    if (c != 0) return c;
                    c = b.getMes().compareTo(a.getMes());
                    if (c != 0) return c;
                    return b.getNumero().compareTo(a.getNumero());
                })
                .map(this::toQuincenaDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AsistenciaDTOs.QuincenaResumenDTO crearQuincena(Integer anio, Integer mes,
                                                           Integer numero) {
        if (quincenaRepo.findByAnioAndMesAndNumero(anio, mes, numero).isPresent())
            throw new BusinessException("Ya existe esa quincena.");

        // Calcular fechas automáticamente
        LocalDate inicio, fin;
        if (numero == 1) {
            inicio = LocalDate.of(anio, mes, 1);
            fin    = LocalDate.of(anio, mes, 15);
        } else {
            inicio = LocalDate.of(anio, mes, 16);
            fin    = LocalDate.of(anio, mes, 1).withDayOfMonth(
                    LocalDate.of(anio, mes, 1).lengthOfMonth());
        }

        Quincena q = Quincena.builder()
                .anio(anio).mes(mes).numero(numero)
                .fechaInicio(inicio).fechaFin(fin)
                .estado(EstadoQuincena.ABIERTA)
                .build();

        return toQuincenaDTO(quincenaRepo.save(q));
    }

    // ── HELPERS ──────────────────────────────────────────────

    private boolean esNocturno(LocalTime horaEntrada) {
        if (horaEntrada == null) return false;
        return horaEntrada.isAfter(NOCTURNO_INICIO.minusMinutes(1))
                || horaEntrada.isBefore(NOCTURNO_FIN.plusMinutes(1));
    }

    /** Color indicador para tiempos no validados */
    private String colorIndicador(Integer minutos) {
        if (minutos == null || minutos == 0) return "gris";
        if (minutos < 10)  return "gris";
        if (minutos < 30)  return "amarillo-palido";
        if (minutos < 60)  return "amarillo";
        return "naranja";
    }

    private AsistenciaResumenDTO toResumenDTO(Asistencia a) {
        Trabajador t = a.getTrabajador();
        return AsistenciaResumenDTO.builder()
                .idAsistencia(a.getIdAsistencia())
                .idTrabajador(t.getIdTrabajador())
                .nombreCompleto(t.getPNombre() + " " + t.getAPaterno() + " " + t.getAMaterno())
                .nroDocumento(t.getNroDocumento())
                .puestoNombre(t.getPuesto() != null ? t.getPuesto().getPuesto() : null)
                .areaNombre(t.getPuesto() != null && t.getPuesto().getArea() != null
                        ? t.getPuesto().getArea().getArea() : null)
                .fecha(a.getFecha().toString())
                .horaEntrada(a.getIngresoReal() != null
                        ? a.getIngresoReal().toString().substring(0, 5) : null)
                .horaSalida(a.getSalidaReal() != null
                        ? a.getSalidaReal().toString().substring(0, 5) : null)
                .estado(a.getEstado().name())
                .tipo(a.getTipo().name())
                .build();
    }

    private AsistenciaRevisionDTO toRevisionDTO(Asistencia a) {
        Trabajador t = a.getTrabajador();
        return AsistenciaRevisionDTO.builder()
                .idAsistencia(a.getIdAsistencia())
                .idTrabajador(t.getIdTrabajador())
                .nombreCompleto(t.getPNombre() + " " + t.getAPaterno() + " " + t.getAMaterno())
                .nroDocumento(t.getNroDocumento())
                .puestoNombre(t.getPuesto() != null ? t.getPuesto().getPuesto() : null)
                .areaNombre(t.getPuesto() != null && t.getPuesto().getArea() != null
                        ? t.getPuesto().getArea().getArea() : null)
                .fecha(a.getFecha().toString())
                .tipo(a.getTipo().name())
                .estado(a.getEstado().name())
                .esNocturno(a.isEsNocturno())
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
                .valMinPrevIng(a.getValMinPrevIng())
                .valMinPostSal(a.getValMinPostSal())
                .revisadoPor(a.getRevisadoPor() != null ? a.getRevisadoPor().getUsername() : null)
                .revisadoEn(a.getRevisadoEn() != null ? a.getRevisadoEn().toString() : null)
                .observacion(a.getObservacion())
                .colorPrev(colorIndicador(a.getMinPrevIngProg()))
                .colorPost(colorIndicador(a.getMinPostSalProg()))
                .build();
    }

    private AsistenciaDTOs.QuincenaResumenDTO toQuincenaDTO(
            com.idat.asistencia.model.entity.Quincena q) {
        long total     = asistenciaRepo.count();  // simplificado
        long pendientes = asistenciaRepo.countByQuincena_IdQuincenaAndEstadoIn(
                q.getIdQuincena(),
                List.of(EstadoAsistencia.CALCULADO, EstadoAsistencia.MARCADO));
        return AsistenciaDTOs.QuincenaResumenDTO.builder()
                .idQuincena(q.getIdQuincena())
                .descripcion(q.getDescripcion())
                .fechaInicio(q.getFechaInicio().toString())
                .fechaFin(q.getFechaFin().toString())
                .inicioReal(q.getInicioReal().toString())
                .finReal(q.getFinReal().toString())
                .estado(q.getEstado().name())
                .pendientesRevision(pendientes)
                .build();
    }

    private String fmt(java.time.LocalTime t) {
        return t != null ? t.toString().substring(0, 5) : null;
    }
}