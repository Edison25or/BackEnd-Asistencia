package com.idat.asistencia.service.impl;

import com.idat.asistencia.dto.ProgramacionDTOs.*;
import com.idat.asistencia.model.entity.Quincena;
import com.idat.asistencia.model.enums.EstadoAsistencia;
import com.idat.asistencia.model.enums.EstadoQuincena;
import com.idat.asistencia.model.enums.TipoAsistencia;
import com.idat.asistencia.repository.QuincenaRepository;
import com.idat.asistencia.exception.BusinessException;
import com.idat.asistencia.exception.ResourceNotFoundException;
import com.idat.asistencia.model.entity.*;
import com.idat.asistencia.repository.*;
import com.idat.asistencia.security.SecurityHelper;
import com.idat.asistencia.service.ProgramacionSemanalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProgramacionSemanalServiceImpl implements ProgramacionSemanalService {

    private final ProgramacionSemanalRepository programacionRepo;
    private final EsquemaHorarioRepository      esquemaRepo;
    private final GrupoTrabajoRepository        grupoRepo;
    private final TrabajadorRepository          trabajadorRepo;
    private final QuincenaRepository            quincenaRepo;
    private final AsistenciaRepository  asistenciaRepository;
    private final HorarioDiaRepository   horarioDiaRepo;
    private final SecurityHelper         securityHelper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM");

    @Override
    public List<ProgramacionResponse> getAll() {
        return programacionRepo.findAllWithRelations()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<ProgramacionResponse> getBySemana(String semanaInicioStr) {
        List<ProgramacionResponse> todas = programacionRepo
                .findBySemanaInicio(LocalDate.parse(semanaInicioStr))
                .stream().map(this::toResponse).collect(Collectors.toList());

        // V3 FIX: Si es TRABAJADOR, filtrar solo sus propias programaciones
        if (securityHelper.esTrabajador()) {
            Long idPropio = securityHelper.getIdTrabajadorAutenticado();
            return todas.stream()
                    .filter(p -> idPropio.equals(p.getIdTrabajador()))
                    .collect(Collectors.toList());
        }

        return todas;
    }

    // ── Asignación individual ─────────────────────────────────
    @Override
    @Transactional
    public ProgramacionResponse crear(ProgramacionRequest request) {
        LocalDate inicio = LocalDate.parse(request.getSemanaInicio());

        validarSemanaNoPassada(inicio);

        if (inicio.getDayOfWeek() != DayOfWeek.SATURDAY)
            throw new BusinessException("La semana debe iniciar en sábado.");

        if (request.getIdTrabajador() == null)
            throw new BusinessException("Debe indicar un trabajador.");

        LocalDate fin      = inicio.plusDays(6);
        EsquemaHorario esq = buscarEsquema(request.getIdEsquema());
        Trabajador trab    = buscarTrabajador(request.getIdTrabajador());

        if (programacionRepo.existsByTrabajadorAndSemana(trab.getIdTrabajador(), inicio))
            throw new BusinessException(
                    "El trabajador '" + trab.getPNombre() + " " + trab.getAPaterno()
                            + "' ya tiene programación para esa semana.");

        return toResponse(programacionRepo.save(
                ProgramacionSemanal.builder()
                        .semanaInicio(inicio).semanaFin(fin)
                        .esquema(esq).trabajador(trab)
                        // asignación individual no tiene grupo
                        .grupoIdSnapshot(null).grupoNombreSnapshot(null)
                        .build()
        ));
    }

    // ── Asignación masiva desde grupo ─────────────────────────
    @Override
    @Transactional
    public ProgramacionBulkResponse crearDesdeGrupo(Integer idGrupo,
                                                    String semanaInicioStr,
                                                    Integer idEsquema) {
        LocalDate inicio = LocalDate.parse(semanaInicioStr);

        validarSemanaNoPassada(inicio);

        if (inicio.getDayOfWeek() != DayOfWeek.SATURDAY)
            throw new BusinessException("La semana debe iniciar en sábado.");

        GrupoTrabajo grupo = grupoRepo.findByIdWithTrabajadores(idGrupo)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado."));

        if (grupo.getTrabajadores().isEmpty())
            throw new BusinessException("El grupo '" + grupo.getNombre() + "' no tiene trabajadores.");

        EsquemaHorario esquema = buscarEsquema(idEsquema);
        LocalDate fin = inicio.plusDays(6);

        int creados = 0, omitidos = 0;
        List<ProgramacionSemanal> nuevos = new ArrayList<>();

        for (Trabajador t : grupo.getTrabajadores()) {
            if (programacionRepo.existsByTrabajadorAndSemana(t.getIdTrabajador(), inicio)) {
                omitidos++;
            } else {
                nuevos.add(ProgramacionSemanal.builder()
                        .semanaInicio(inicio).semanaFin(fin)
                        .esquema(esquema).trabajador(t)
                        // ── Snapshot: foto del grupo en este momento ──────
                        // Aunque el grupo cambie después, esta información
                        // permite reconstruir la tarjeta visual correctamente.
                        .grupoIdSnapshot(grupo.getIdGrupo())
                        .grupoNombreSnapshot(grupo.getNombre())
                        .build());
                creados++;
            }
        }

        if (!nuevos.isEmpty()) programacionRepo.saveAll(nuevos);

        String label = "Sáb " + inicio.format(FMT) + " → Vie " + fin.format(FMT);
        return ProgramacionBulkResponse.builder()
                .creados(creados).omitidos(omitidos).semanaLabel(label)
                .build();
    }

    // ── Eliminar ──────────────────────────────────────────────
    @Override
    @Transactional
    public void eliminar(Long id) {
        ProgramacionSemanal prog = programacionRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Programación no encontrada."));

        // Bloqueo Etapa 1: semanas pasadas son de solo lectura
        validarSemanaNoPassada(prog.getSemanaInicio());

        programacionRepo.delete(prog);
    }

    // ── Confirmar semana + generar pre-registros ─────────────
    @Override
    @Transactional
    public ConfirmarSemanaResponse confirmarSemana(String semanaInicioStr) {
        LocalDate inicio = LocalDate.parse(semanaInicioStr);

        // Bloqueo: no se puede confirmar una semana ya pasada
        validarSemanaNoPassada(inicio);

        LocalDate fin = inicio.plusDays(6);

        // ── 1. Obtener todas las programaciones de la semana ──
        List<ProgramacionSemanal> progs = programacionRepo.findBySemanaInicio(inicio);

        if (progs.isEmpty())
            throw new BusinessException(
                    "No hay trabajadores programados para la semana del "
                            + inicio.format(FMT) + ".");

        // ── 2. Determinar quincena ─────────────────────────────
        // La semana puede quedar dividida entre dos quincenas.
        // Usamos el sábado (inicio) para determinar la quincena principal.
        Quincena quincena = quincenaRepo
                .findByFechaAproximada(inicio, inicio.atStartOfDay().toLocalTime())
                .orElse(null);

        if (quincena == null)
            throw new BusinessException(
                    "No existe una quincena abierta que contenga la semana del "
                            + inicio.format(FMT) + ". Crea la quincena primero en "
                            + "Revisión de Asistencias.");

        if (quincena.getEstado() == EstadoQuincena.CERRADA)
            throw new BusinessException(
                    "La quincena «" + quincena.getDescripcion()
                            + "» ya está cerrada. No se pueden generar nuevos pre-registros.");

        // ── 3. Generar pre-registros de asistencia ─────────────
        // Genera pre-registros de asistencia: uno por trabajador × día laborable.
        int[] contadores = new int[]{0, 0}; // [creados, omitidos]

        for (ProgramacionSemanal prog : progs) {
            Trabajador t    = prog.getTrabajador();
            EsquemaHorario e = prog.getEsquema();

            for (LocalDate dia = inicio; !dia.isAfter(fin); dia = dia.plusDays(1)) {
                int diaSemana = dia.getDayOfWeek().getValue(); // 1=Lun..7=Dom

                HorarioDia hd =
                        horarioDiaRepo.findByEsquemaAndDia(e.getIdEsquema(), diaSemana)
                                .orElse(null);

                // Saltar días de descanso o sin horario definido
                if (hd == null || Boolean.TRUE.equals(hd.getEsDescanso())) continue;

                // No duplicar pre-registros
                if (asistenciaRepository.existsByTrabajador_IdTrabajadorAndFechaAndTipo(
                        t.getIdTrabajador(), dia,
                        TipoAsistencia.PROGRAMADA)) {
                    contadores[1]++;
                    continue;
                }

                // Crear pre-registro
                Asistencia preReg =
                        Asistencia.builder()
                                .trabajador(t)
                                .fecha(dia)
                                .tipo(TipoAsistencia.PROGRAMADA)
                                .estado(EstadoAsistencia.PENDIENTE)
                                .esquema(e)
                                .programacion(prog)
                                .quincena(quincena)
                                .esNocturno(esNocturnoHora(hd.getHoraEntrada()))
                                .ingresoProg(hd.getHoraEntrada())
                                .salidaProg(hd.getHoraSalidaCalculada())
                                .minRefrigerioProg(hd.getMinutosRefrigerio())
                                .minNetosProg(hd.getMinutosNetos())
                                .minExtraProg(hd.getMinutosExtraProgramado())
                                .build();

                asistenciaRepository.save(preReg);
                contadores[0]++;
            }
        }

        String label = "Sáb " + inicio.format(FMT) + " → Vie " + fin.format(FMT);
        return ConfirmarSemanaResponse.builder()
                .semanaLabel(label)
                .totalTrabajadores(progs.size())
                .preRegistrosCreados(contadores[0])
                .preRegistrosOmitidos(contadores[1])
                .quincenaDescripcion(quincena.getDescripcion())
                .idQuincena(quincena.getIdQuincena())
                .build();
    }

    /** Clasifica si una hora de ingreso corresponde a turno nocturno */
    private boolean esNocturnoHora(java.time.LocalTime h) {
        if (h == null) return false;
        return h.isAfter(java.time.LocalTime.of(18, 59))
                || h.isBefore(java.time.LocalTime.of(5, 1));
    }

    // ── Validación de semana pasada ───────────────────────────
    /**
     * Lanza BusinessException si la semana ya terminó (semanaFin < hoy).
     * Esto protege el historial: ninguna programación de una semana ya
     * transcurrida puede crearse ni eliminarse.
     */
    private void validarSemanaNoPassada(LocalDate semanaInicio) {
        LocalDate semanaFin = semanaInicio.plusDays(6);
        if (semanaFin.isBefore(LocalDate.now())) {
            throw new BusinessException(
                    "No se pueden modificar programaciones de semanas pasadas. " +
                            "La semana del " + semanaInicio.format(FMT) +
                            " → " + semanaFin.format(FMT) + " ya ha concluido."
            );
        }
    }

    // ── Helpers ───────────────────────────────────────────────
    private EsquemaHorario buscarEsquema(Integer id) {
        return esquemaRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Esquema no encontrado."));
    }

    private Trabajador buscarTrabajador(Long id) {
        return trabajadorRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado."));
    }

    private ProgramacionResponse toResponse(ProgramacionSemanal p) {
        String label = "Sáb " + p.getSemanaInicio().format(FMT)
                + " → Vie " + p.getSemanaFin().format(FMT);
        Trabajador t    = p.getTrabajador();
        Puesto puesto   = t.getPuesto();
        boolean passada = p.getSemanaFin().isBefore(LocalDate.now());

        return ProgramacionResponse.builder()
                .idProgramacion(p.getIdProgramacion())
                .semanaInicio(p.getSemanaInicio().toString())
                .semanaFin(p.getSemanaFin().toString())
                .semanaLabel(label)
                .idEsquema(p.getEsquema().getIdEsquema())
                .esquemaNombre(p.getEsquema().getNombre())
                .idTrabajador(t.getIdTrabajador())
                .trabajadorNombre(t.getPNombre() + " " + t.getAPaterno() + " " + t.getAMaterno())
                .trabajadorDocumento(t.getNroDocumento())
                .puestoNombre(puesto != null ? puesto.getPuesto() : null)
                .areaNombre(puesto != null && puesto.getArea() != null ? puesto.getArea().getArea() : null)
                // Snapshot del grupo
                .grupoIdSnapshot(p.getGrupoIdSnapshot())
                .grupoNombreSnapshot(p.getGrupoNombreSnapshot())
                // Flag de solo lectura para el frontend
                .semanaPassada(passada)
                .build();
    }
}