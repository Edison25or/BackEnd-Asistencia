package com.idat.asistencia.service.impl;

import com.idat.asistencia.dto.EsquemaHorarioDTOs.*;
import com.idat.asistencia.exception.BusinessException;
import com.idat.asistencia.exception.ResourceNotFoundException;
import com.idat.asistencia.model.entity.EsquemaHorario;
import com.idat.asistencia.model.entity.HorarioDia;
import com.idat.asistencia.model.entity.Turno;
import com.idat.asistencia.repository.EsquemaHorarioRepository;
import com.idat.asistencia.repository.ProgramacionSemanalRepository;
import com.idat.asistencia.repository.TurnoRepository;
import com.idat.asistencia.security.SecurityHelper;
import com.idat.asistencia.service.AuditoriaService;
import com.idat.asistencia.service.EsquemaHorarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Esquemas de horario (CU12).
 *
 * ============================================================
 * QUE CAMBIA
 * ============================================================
 * 1. toleranciaMinutos se desdobla en toleranciaTardanza,
 *    toleranciaPrevia y toleranciaPosterior (RN-17). Las dos ultimas
 *    definen la ventana de la jornada, que es lo que permite localizar
 *    una marcacion sin depender de la fecha calendario.
 *
 * 2. El turno es obligatorio (RN-18). Reemplaza la clasificacion por
 *    umbral horario fijo 19:00-05:00 que el prototipo tenia duplicada en
 *    dos servicios.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EsquemaHorarioServiceImpl implements EsquemaHorarioService {

    private final EsquemaHorarioRepository      repo;
    private final TurnoRepository               turnoRepo;
    private final ProgramacionSemanalRepository programacionRepo;
    private final SecurityHelper                securityHelper;
    private final AuditoriaService              auditoria;

    private static final String TABLA = "esquemas_horario";

    @Override
    public List<EsquemaResponse> getAll() {
        return repo.findAllVigentesActivos()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<EsquemaGrupoResponse> getAllAgrupados() {
        return repo.findAllVersionesActivas().stream().map(v ->
                EsquemaGrupoResponse.builder()
                        .grupoNombre(v.getGrupoNombre())
                        .versionActiva(toResponse(v))
                        .versiones(repo.findByGrupoNombreOrderByVersionDesc(v.getGrupoNombre())
                                .stream().map(this::toResponse).collect(Collectors.toList()))
                        .build()
        ).collect(Collectors.toList());
    }

    @Override
    public EsquemaResponse getById(Integer id) {
        EsquemaHorario e = buscarOLanzar(id);

        if (securityHelper.esTrabajador()) {
            Long idTrabajador = securityHelper.getIdTrabajadorAutenticado();
            if (!programacionRepo.existsByEsquema_IdEsquemaAndTrabajador_IdTrabajador(
                    id, idTrabajador))
                throw new BusinessException("No tienes permiso para acceder a este esquema.");
        }
        return toResponse(e);
    }

    @Override
    @Transactional
    public EsquemaResponse crear(EsquemaRequest request) {
        if (repo.existsByGrupoNombreIgnoreCase(request.getNombre()))
            throw new BusinessException(
                    "Ya existe un esquema con el nombre '" + request.getNombre() + "'.");

        Turno turno = buscarTurno(request.getIdTurno());

        EsquemaHorario esquema = EsquemaHorario.builder()
                .nombre(request.getNombre())
                .grupoNombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .turno(turno)
                .toleranciaTardanza(nz(request.getToleranciaTardanza(), 10))
                .toleranciaPrevia(nz(request.getToleranciaPrevia(), 15))
                .toleranciaPosterior(nz(request.getToleranciaPosterior(), 15))
                .version(1)
                .vigenteDesde(LocalDate.now())
                .vigenteHasta(null)
                .activo(true)
                .build();

        agregarDias(esquema, request.getHorariosDia());
        EsquemaHorario saved = repo.save(esquema);

        auditoria.registrar(TABLA, saved.getIdEsquema().longValue(), "CREAR");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public EsquemaResponse crearNuevaVersion(String grupoNombre, NuevaVersionRequest request) {
        LocalDate nuevaVigencia = LocalDate.parse(request.getVigenteDesde());

        EsquemaHorario actual = repo.findByGrupoNombreAndVigenteHastaIsNull(grupoNombre)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontro version activa del esquema '" + grupoNombre + "'."));

        if (!nuevaVigencia.isAfter(actual.getVigenteDesde()))
            throw new BusinessException(
                    "La fecha de vigencia debe ser posterior a " + actual.getVigenteDesde() + ".");

        // La version anterior se conserva intacta para las semanas ya
        // programadas con ella (RT-06)
        actual.setVigenteHasta(nuevaVigencia.minusDays(1));
        actual.setActivo(false);
        repo.save(actual);

        Integer maxVersion = repo.findMaxVersionByGrupoNombre(grupoNombre);
        int siguiente = (maxVersion != null ? maxVersion : 0) + 1;

        Turno turno = request.getIdTurno() != null
                ? buscarTurno(request.getIdTurno()) : actual.getTurno();

        EsquemaHorario nueva = EsquemaHorario.builder()
                .nombre(grupoNombre + " v" + siguiente)
                .grupoNombre(grupoNombre)
                .descripcion(request.getDescripcion() != null
                        ? request.getDescripcion() : actual.getDescripcion())
                .turno(turno)
                .toleranciaTardanza(nz(request.getToleranciaTardanza(), actual.getToleranciaTardanza()))
                .toleranciaPrevia(nz(request.getToleranciaPrevia(), actual.getToleranciaPrevia()))
                .toleranciaPosterior(nz(request.getToleranciaPosterior(), actual.getToleranciaPosterior()))
                .version(siguiente)
                .vigenteDesde(nuevaVigencia)
                .vigenteHasta(null)
                .activo(true)
                .build();

        agregarDias(nueva, request.getHorariosDia());
        EsquemaHorario saved = repo.save(nueva);

        auditoria.registrarCampo(TABLA, saved.getIdEsquema().longValue(),
                "NUEVA_VERSION", "version",
                String.valueOf(actual.getVersion()), String.valueOf(saved.getVersion()));

        return toResponse(saved);
    }

    @Override
    @Transactional
    public EsquemaResponse toggleActivo(Integer id) {
        EsquemaHorario e = buscarOLanzar(id);

        if (e.isCerrado())
            throw new BusinessException("No se puede habilitar o deshabilitar una version cerrada.");

        e.setActivo(!e.isActivo());
        EsquemaHorario saved = repo.save(e);

        auditoria.registrar(TABLA, id.longValue(),
                saved.isActivo() ? "HABILITAR" : "DESHABILITAR");

        return toResponse(saved);
    }

    @Override
    public long contarProgramaciones(Integer idEsquema) {
        return repo.countProgramacionesByEsquema(idEsquema);
    }

    // ---------- Helpers ----------

    private void agregarDias(EsquemaHorario esquema, List<HorarioDiaRequest> dias) {
        // Exactamente siete dias, sin excepcion (RT-03)
        if (dias == null || dias.size() != 7)
            throw new BusinessException("Debe enviar exactamente 7 dias.");

        int orden = 1;
        for (HorarioDiaRequest d : dias) {
            boolean descanso = Boolean.TRUE.equals(d.getEsDescanso());

            if (!descanso && (d.getHoraEntrada() == null || d.getHoraEntrada().isBlank()))
                throw new BusinessException(
                        "El dia " + d.getDiaSemana() + " no es descanso y requiere hora de entrada.");

            esquema.getHorariosDia().add(HorarioDia.builder()
                    .esquema(esquema)
                    .diaSemana(d.getDiaSemana())
                    .ordenDia(orden++)
                    .esDescanso(descanso)
                    .horaEntrada(descanso ? null : LocalTime.parse(d.getHoraEntrada()))
                    .minutosRefrigerio(d.getMinutosRefrigerio())
                    .minutosNetos(d.getMinutosNetos())
                    .minutosExtraProgramado(d.getMinutosExtraProgramado() != null
                            ? d.getMinutosExtraProgramado() : 0)
                    .build());
        }
    }

    private Turno buscarTurno(Integer id) {
        return turnoRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Turno no encontrado: " + id));
    }

    private EsquemaHorario buscarOLanzar(Integer id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Esquema no encontrado: " + id));
    }

    private EsquemaResponse toResponse(EsquemaHorario e) {
        int totalNetos = 0, totalExtra = 0;

        // Orden de presentacion: la semana laboral corre de sabado a viernes
        List<Integer> ordenDias = List.of(6, 7, 1, 2, 3, 4, 5);

        List<HorarioDiaResponse> dias = e.getHorariosDia().stream()
                .sorted((a, b) -> Integer.compare(
                        ordenDias.indexOf(a.getDiaSemana()),
                        ordenDias.indexOf(b.getDiaSemana())))
                .map(d -> {
                    LocalTime salida = d.getHoraSalidaCalculada();
                    // La salida cae al dia siguiente cuando es anterior a
                    // la entrada. Es el caso normal del turno noche.
                    boolean diaSiguiente = salida != null && d.getHoraEntrada() != null
                            && !salida.isAfter(d.getHoraEntrada());

                    return HorarioDiaResponse.builder()
                            .idHorarioDia(d.getIdHorarioDia())
                            .diaSemana(d.getDiaSemana())
                            .nombreDia(d.getNombreDia())
                            .esDescanso(d.getEsDescanso())
                            .horaEntrada(d.getHoraEntrada() != null
                                    ? d.getHoraEntrada().toString().substring(0, 5) : null)
                            .minutosRefrigerio(d.getMinutosRefrigerio())
                            .minutosNetos(d.getMinutosNetos())
                            .minutosExtraProgramado(d.getMinutosExtraProgramado())
                            .horaSalida(salida != null ? salida.toString().substring(0, 5) : null)
                            .salidaDiaSiguiente(diaSiguiente)
                            .horasNetasFormato(fmt(d.getMinutosNetos()))
                            .extraFormato(fmt(d.getMinutosExtraProgramado()))
                            .build();
                }).collect(Collectors.toList());

        for (HorarioDia d : e.getHorariosDia()) {
            if (!Boolean.TRUE.equals(d.getEsDescanso())) {
                totalNetos += d.getMinutosNetos() != null ? d.getMinutosNetos() : 0;
                totalExtra += d.getMinutosExtraProgramado() != null
                        ? d.getMinutosExtraProgramado() : 0;
            }
        }

        Turno turno = e.getTurno();

        return EsquemaResponse.builder()
                .idEsquema(e.getIdEsquema())
                .nombre(e.getNombre())
                .grupoNombre(e.getGrupoNombre())
                .descripcion(e.getDescripcion())
                .idTurno(turno != null ? turno.getIdTurno() : null)
                .turnoNombre(turno != null ? turno.getNombre() : null)
                .turnoCruzaMedianoche(turno != null && turno.isCruzaMedianoche())
                .toleranciaTardanza(e.getToleranciaTardanza())
                .toleranciaPrevia(e.getToleranciaPrevia())
                .toleranciaPosterior(e.getToleranciaPosterior())
                .version(e.getVersion())
                .vigenteDesde(e.getVigenteDesde() != null ? e.getVigenteDesde().toString() : null)
                .vigenteHasta(e.getVigenteHasta() != null ? e.getVigenteHasta().toString() : null)
                .activo(e.isActivo())
                .vigente(e.isVigente())
                .horariosDia(dias)
                .totalHorasNetas(fmt(totalNetos))
                .totalHorasExtra(fmt(totalExtra))
                .totalHorasBrutas(fmt(totalNetos + totalExtra))
                .tieneProgramaciones(repo.countProgramacionesByEsquema(e.getIdEsquema()) > 0)
                .build();
    }

    private String fmt(Integer min) {
        if (min == null || min == 0) return "00:00";
        return String.format("%02d:%02d", min / 60, min % 60);
    }

    private Integer nz(Integer valor, Integer porDefecto) {
        return valor != null ? valor : porDefecto;
    }
}
