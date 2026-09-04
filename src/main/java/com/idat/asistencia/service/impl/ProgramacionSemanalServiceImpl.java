package com.idat.asistencia.service.impl;

import com.idat.asistencia.dto.ProgramacionDTOs.*;
import com.idat.asistencia.exception.BusinessException;
import com.idat.asistencia.exception.ResourceNotFoundException;
import com.idat.asistencia.model.entity.*;
import com.idat.asistencia.repository.*;
import com.idat.asistencia.security.SecurityHelper;
import com.idat.asistencia.service.AuditoriaService;
import com.idat.asistencia.service.PreRegistroService;
import com.idat.asistencia.service.ProgramacionSemanalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Programacion semanal de horarios (CU14).
 *
 * ============================================================
 * QUE CAMBIA
 * ============================================================
 * 1. La generacion de pre-registros sale de aqui y pasa a
 *    PreRegistroService. Estaba duplicada con AsistenciaServiceImpl, y de
 *    las dos copias solo esta se ejecutaba: la otra ni siquiera estaba
 *    declarada en su interfaz.
 *
 * 2. Desaparece la excepcion "No existe una quincena abierta... Crea la
 *    quincena primero en Revision de Asistencias". La quincena se
 *    autogenera (RN-35).
 *
 * 3. La quincena ya no se resuelve una vez con el sabado de inicio. Una
 *    semana que cruza el corte del 15 o de fin de mes pertenece a DOS
 *    quincenas, y cada dia se asigna a la que le corresponde.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProgramacionSemanalServiceImpl implements ProgramacionSemanalService {

    private final ProgramacionSemanalRepository programacionRepo;
    private final EsquemaHorarioRepository      esquemaRepo;
    private final GrupoTrabajoRepository        grupoRepo;
    private final TrabajadorRepository          trabajadorRepo;
    private final PreRegistroService            preRegistroService;
    private final SecurityHelper                securityHelper;
    private final AuditoriaService              auditoria;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final String TABLA = "programaciones_semanales";

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

        if (securityHelper.esTrabajador()) {
            Long idPropio = securityHelper.getIdTrabajadorAutenticado();
            return todas.stream()
                    .filter(p -> idPropio.equals(p.getIdTrabajador()))
                    .collect(Collectors.toList());
        }
        return todas;
    }

    // ---------- Asignacion individual ----------

    @Override
    @Transactional
    public ProgramacionResponse crear(ProgramacionRequest request) {
        LocalDate inicio = LocalDate.parse(request.getSemanaInicio());
        validarSemanaNoPasada(inicio);

        if (inicio.getDayOfWeek() != DayOfWeek.SATURDAY)
            throw new BusinessException("La semana debe iniciar en sabado.");

        if (request.getIdTrabajador() == null)
            throw new BusinessException("Debe indicar un trabajador.");

        LocalDate      fin = inicio.plusDays(6);
        EsquemaHorario esq = buscarEsquema(request.getIdEsquema());
        Trabajador     t   = buscarTrabajador(request.getIdTrabajador());

        if (programacionRepo.existsByTrabajadorAndSemana(t.getIdTrabajador(), inicio))
            throw new BusinessException("El trabajador '" + t.getNombreCompleto()
                    + "' ya tiene programacion para esa semana.");

        return toResponse(programacionRepo.save(ProgramacionSemanal.builder()
                .semanaInicio(inicio).semanaFin(fin)
                .esquema(esq).trabajador(t)
                .grupoIdSnapshot(null).grupoNombreSnapshot(null)
                .build()));
    }

    // ---------- Asignacion masiva desde grupo ----------

    @Override
    @Transactional
    public ProgramacionBulkResponse crearDesdeGrupo(Integer idGrupo,
                                                    String semanaInicioStr,
                                                    Integer idEsquema) {
        LocalDate inicio = LocalDate.parse(semanaInicioStr);
        validarSemanaNoPasada(inicio);

        if (inicio.getDayOfWeek() != DayOfWeek.SATURDAY)
            throw new BusinessException("La semana debe iniciar en sabado.");

        GrupoTrabajo grupo = grupoRepo.findByIdWithTrabajadores(idGrupo)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado."));

        // Los miembros se leen desde el lado inverso de la relacion, que
        // ahora vive en Trabajador.grupoTrabajo.
        List<Trabajador> miembros = trabajadorRepo.findByGrupoTrabajo_IdGrupo(idGrupo);

        if (miembros.isEmpty())
            throw new BusinessException("El grupo '" + grupo.getNombre()
                    + "' no tiene trabajadores.");

        EsquemaHorario esquema = buscarEsquema(idEsquema);
        LocalDate fin = inicio.plusDays(6);

        int creados = 0, omitidos = 0;
        List<ProgramacionSemanal> nuevos = new ArrayList<>();

        for (Trabajador t : miembros) {
            if (programacionRepo.existsByTrabajadorAndSemana(t.getIdTrabajador(), inicio)) {
                omitidos++;
            } else {
                nuevos.add(ProgramacionSemanal.builder()
                        .semanaInicio(inicio).semanaFin(fin)
                        .esquema(esquema).trabajador(t)
                        // Copia de los datos del grupo. Permite
                        // reconstruir la programacion aunque el grupo se
                        // modifique o elimine despues.
                        .grupoIdSnapshot(grupo.getIdGrupo())
                        .grupoNombreSnapshot(grupo.getNombre())
                        .build());
                creados++;
            }
        }

        if (!nuevos.isEmpty()) programacionRepo.saveAll(nuevos);

        return ProgramacionBulkResponse.builder()
                .creados(creados).omitidos(omitidos)
                .semanaLabel(etiqueta(inicio, fin))
                .build();
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        ProgramacionSemanal prog = programacionRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Programacion no encontrada."));
        validarSemanaNoPasada(prog.getSemanaInicio());
        programacionRepo.delete(prog);
    }

    // ---------- Confirmar semana ----------

    @Override
    @Transactional
    public ConfirmarSemanaResponse confirmarSemana(String semanaInicioStr) {
        LocalDate inicio = LocalDate.parse(semanaInicioStr);
        validarSemanaNoPasada(inicio);

        LocalDate fin = inicio.plusDays(6);

        List<ProgramacionSemanal> progs = programacionRepo.findBySemanaInicio(inicio);
        if (progs.isEmpty())
            throw new BusinessException("No hay trabajadores programados para la semana del "
                    + inicio.format(FMT) + ".");

        // Toda la logica vive en PreRegistroService: ventana horaria,
        // resolucion de quincena por dia, marcado de feriados y
        // neutralizacion de ausencias ya registradas.
        PreRegistroService.ResultadoGeneracion r =
                preRegistroService.generar(progs, inicio, fin);

        String quincenas = r.quincenas().stream()
                .map(Quincena::getDescripcion)
                .distinct()
                .collect(Collectors.joining(" y "));

        auditoria.registrarCampo(TABLA, null, "CONFIRMAR_SEMANA", "semana", null,
                etiqueta(inicio, fin) + ": " + r.creados() + " pre-registros creados, "
                        + r.omitidos() + " omitidos, quincenas: " + quincenas);

        return ConfirmarSemanaResponse.builder()
                .semanaLabel(etiqueta(inicio, fin))
                .totalTrabajadores(progs.size())
                .preRegistrosCreados(r.creados())
                .preRegistrosOmitidos(r.omitidos())
                .preRegistrosEnFeriado(r.enFeriado())
                .quincenaDescripcion(quincenas)
                .idsQuincenas(r.quincenas().stream()
                        .map(Quincena::getIdQuincena).distinct().toList())
                .build();
    }

    // ---------- Helpers ----------

    private void validarSemanaNoPasada(LocalDate semanaInicio) {
        LocalDate semanaFin = semanaInicio.plusDays(6);
        if (semanaFin.isBefore(LocalDate.now()))
            throw new BusinessException(
                    "No se pueden modificar programaciones de semanas pasadas. La semana del "
                            + semanaInicio.format(FMT) + " a " + semanaFin.format(FMT)
                            + " ya ha concluido.");
    }

    private String etiqueta(LocalDate inicio, LocalDate fin) {
        return "Sab " + inicio.format(FMT) + " a Vie " + fin.format(FMT);
    }

    private EsquemaHorario buscarEsquema(Integer id) {
        return esquemaRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Esquema no encontrado."));
    }

    private Trabajador buscarTrabajador(Long id) {
        return trabajadorRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado."));
    }

    private ProgramacionResponse toResponse(ProgramacionSemanal p) {
        Trabajador t      = p.getTrabajador();
        Puesto     puesto = t.getPuesto();

        return ProgramacionResponse.builder()
                .idProgramacion(p.getIdProgramacion())
                .semanaInicio(p.getSemanaInicio().toString())
                .semanaFin(p.getSemanaFin().toString())
                .semanaLabel(etiqueta(p.getSemanaInicio(), p.getSemanaFin()))
                .idEsquema(p.getEsquema().getIdEsquema())
                .esquemaNombre(p.getEsquema().getNombre())
                .turnoNombre(p.getEsquema().getTurno() != null
                        ? p.getEsquema().getTurno().getNombre() : null)
                .idTrabajador(t.getIdTrabajador())
                .trabajadorNombre(t.getNombreCompleto())
                .trabajadorDocumento(t.getNroDocumento())
                .puestoNombre(puesto != null ? puesto.getPuesto() : null)
                .areaNombre(puesto != null && puesto.getArea() != null
                        ? puesto.getArea().getArea() : null)
                .grupoIdSnapshot(p.getGrupoIdSnapshot())
                .grupoNombreSnapshot(p.getGrupoNombreSnapshot())
                .semanaPassada(p.getSemanaFin().isBefore(LocalDate.now()))
                .build();
    }
}
