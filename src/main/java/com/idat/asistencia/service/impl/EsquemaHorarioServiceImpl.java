package com.idat.asistencia.service.impl;

import com.idat.asistencia.dto.EsquemaHorarioDTOs.*;
import com.idat.asistencia.exception.BusinessException;
import com.idat.asistencia.exception.ResourceNotFoundException;
import com.idat.asistencia.model.entity.EsquemaHorario;
import com.idat.asistencia.model.entity.HorarioDia;
import com.idat.asistencia.repository.EsquemaHorarioRepository;
import com.idat.asistencia.repository.ProgramacionSemanalRepository;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EsquemaHorarioServiceImpl implements EsquemaHorarioService {

    private final EsquemaHorarioRepository      repo;
    private final ProgramacionSemanalRepository  programacionRepo;
    private final SecurityHelper                 securityHelper;
    private final AuditoriaService               auditoriaService;

    private static final String TABLA = "esquemas_horario";

    // ── Para dropdowns: solo versiones vigentes y activas ─────
    @Override
    public List<EsquemaResponse> getAll() {
        return repo.findAllVigentesActivos()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Para pantalla de gestión: agrupados con historial ─────
    @Override
    public List<EsquemaGrupoResponse> getAllAgrupados() {
        // Obtener todas las versiones activas (una por grupo)
        List<EsquemaHorario> vigentes = repo.findAllVersionesActivas();

        return vigentes.stream().map(v -> {
            List<EsquemaHorario> versiones =
                    repo.findByGrupoNombreOrderByVersionDesc(v.getGrupoNombre());

            return EsquemaGrupoResponse.builder()
                    .grupoNombre(v.getGrupoNombre())
                    .versionActiva(toResponse(v))
                    .versiones(versiones.stream().map(this::toResponse).collect(Collectors.toList()))
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public EsquemaResponse getById(Integer id) {
        EsquemaHorario esquema = buscarOLanzar(id);

        // V4: Si es TRABAJADOR, verificar que el esquema pertenezca a su programación
        if (securityHelper.esTrabajador()) {
            Long idTrabajador = securityHelper.getIdTrabajadorAutenticado();

            boolean tieneAcceso = programacionRepo
                    .existsByEsquema_IdEsquemaAndTrabajador_IdTrabajador(id, idTrabajador);

            if (!tieneAcceso)
                throw new BusinessException(
                        "No tienes permiso para acceder a este esquema.");
        }

        return toResponse(esquema);
    }

    // ── Crear nuevo esquema (versión 1) ───────────────────────
    @Override
    @Transactional
    public EsquemaResponse crear(EsquemaRequest request) {
        if (repo.existsByGrupoNombreIgnoreCase(request.getNombre()))
            throw new BusinessException(
                    "Ya existe un esquema con el nombre «" + request.getNombre() + "».");

        EsquemaHorario esquema = EsquemaHorario.builder()
                .nombre(request.getNombre())
                .grupoNombre(request.getNombre())   // v1: nombre == grupoNombre
                .descripcion(request.getDescripcion())
                .toleranciaMinutos(request.getToleranciaMinutos())
                .version(1)
                .vigenteDesde(LocalDate.now())
                .vigenteHasta(null)                  // activo
                .activo(true)
                .build();

        agregarDias(esquema, request.getHorariosDia(), request.getToleranciaMinutos());
        EsquemaHorario saved = repo.save(esquema);

        auditoriaService.registrar(TABLA, saved.getIdEsquema().longValue(), "CREAR");
        return toResponse(saved);
    }

    // ── Crear nueva versión ───────────────────────────────────
    @Override
    @Transactional
    public EsquemaResponse crearNuevaVersion(String grupoNombre, NuevaVersionRequest request) {
        LocalDate nuevaVigencia = LocalDate.parse(request.getVigenteDesde());

        // Buscar versión activa actual
        EsquemaHorario actual = repo.findByGrupoNombreAndVigenteHastaIsNull(grupoNombre)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró versión activa del esquema «" + grupoNombre + "»."));

        if (!nuevaVigencia.isAfter(actual.getVigenteDesde()))
            throw new BusinessException(
                    "La fecha de vigencia de la nueva versión debe ser posterior a " +
                            actual.getVigenteDesde() + ".");

        // Cerrar la versión actual
        actual.setVigenteHasta(nuevaVigencia.minusDays(1));
        actual.setActivo(false);
        repo.save(actual);

        // Calcular siguiente número de versión
        Integer maxVersion = repo.findMaxVersionByGrupoNombre(grupoNombre);
        int siguienteVersion = (maxVersion != null ? maxVersion : 0) + 1;

        Integer tolerancia = request.getToleranciaMinutos() != null
                ? request.getToleranciaMinutos()
                : actual.getToleranciaMinutos();

        // Crear la nueva versión
        EsquemaHorario nueva = EsquemaHorario.builder()
                .nombre(grupoNombre + " v" + siguienteVersion)
                .grupoNombre(grupoNombre)
                .descripcion(request.getDescripcion() != null
                        ? request.getDescripcion() : actual.getDescripcion())
                .toleranciaMinutos(tolerancia)
                .version(siguienteVersion)
                .vigenteDesde(nuevaVigencia)
                .vigenteHasta(null)
                .activo(true)
                .build();

        agregarDias(nueva, request.getHorariosDia(), tolerancia);
        EsquemaHorario saved = repo.save(nueva);

        auditoriaService.registrarCampo(TABLA, saved.getIdEsquema().longValue(),
                "NUEVA_VERSION", "version",
                String.valueOf(actual.getVersion()),
                String.valueOf(saved.getVersion()));

        return toResponse(saved);
    }

    // ── Toggle activo/inactivo (reemplaza eliminar) ───────────
    @Override
    @Transactional
    public EsquemaResponse toggleActivo(Integer id) {
        EsquemaHorario e = buscarOLanzar(id);

        if (e.isCerrado())
            throw new BusinessException(
                    "No se puede habilitar/deshabilitar una versión cerrada.");

        e.setActivo(!e.isActivo());
        EsquemaHorario saved = repo.save(e);

        auditoriaService.registrar(TABLA, id.longValue(),
                saved.isActivo() ? "HABILITAR" : "DESHABILITAR");

        return toResponse(saved);
    }

    // ── Contar programaciones (para modal de advertencia) ─────
    @Override
    public long contarProgramaciones(Integer idEsquema) {
        return repo.countProgramacionesByEsquema(idEsquema);
    }

    // ── HELPERS ───────────────────────────────────────────────

    private void agregarDias(EsquemaHorario esquema,
                             List<com.idat.asistencia.dto.EsquemaHorarioDTOs.HorarioDiaRequest> dias,
                             Integer tolerancia) {
        if (dias == null || dias.size() != 7)
            throw new BusinessException("Debe enviar exactamente 7 días.");

        int orden = 1;
        for (var d : dias) {
            esquema.getHorariosDia().add(HorarioDia.builder()
                    .esquema(esquema)
                    .diaSemana(d.getDiaSemana())
                    .ordenDia(orden++)
                    .esDescanso(d.getEsDescanso())
                    .horaEntrada(d.getEsDescanso() || d.getHoraEntrada() == null
                            ? null : LocalTime.parse(d.getHoraEntrada()))
                    .minutosRefrigerio(d.getMinutosRefrigerio())
                    .minutosNetos(d.getMinutosNetos())
                    .minutosExtraProgramado(d.getMinutosExtraProgramado() != null
                            ? d.getMinutosExtraProgramado() : 0)
                    .build());
        }
    }

    private EsquemaHorario buscarOLanzar(Integer id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Esquema no encontrado: " + id));
    }

    private EsquemaResponse toResponse(EsquemaHorario e) {
        int totalNetos = 0, totalExtra = 0;

        // Reordenar: Sáb(6), Dom(7), Lun(1)...Vie(5)
        List<Integer> ordenDias = List.of(6, 7, 1, 2, 3, 4, 5);
        List<HorarioDiaResponse> dias = e.getHorariosDia().stream()
                .sorted((a, b) -> Integer.compare(
                        ordenDias.indexOf(a.getDiaSemana()),
                        ordenDias.indexOf(b.getDiaSemana())))
                .map(d -> {
                    String salida = d.getHoraSalidaCalculada() != null
                            ? d.getHoraSalidaCalculada().toString().substring(0, 5) : null;
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
                            .horaSalida(salida)
                            .horasNetasFormato(fmt(d.getMinutosNetos()))
                            .extraFormato(fmt(d.getMinutosExtraProgramado()))
                            .build();
                }).collect(Collectors.toList());

        for (HorarioDia d : e.getHorariosDia()) {
            if (!Boolean.TRUE.equals(d.getEsDescanso())) {
                totalNetos += d.getMinutosNetos()           != null ? d.getMinutosNetos()           : 0;
                totalExtra += d.getMinutosExtraProgramado() != null ? d.getMinutosExtraProgramado() : 0;
            }
        }

        long nProg = repo.countProgramacionesByEsquema(e.getIdEsquema());

        return EsquemaResponse.builder()
                .idEsquema(e.getIdEsquema())
                .nombre(e.getNombre())
                .grupoNombre(e.getGrupoNombre())
                .descripcion(e.getDescripcion())
                .toleranciaMinutos(e.getToleranciaMinutos())
                .version(e.getVersion())
                .vigenteDesde(e.getVigenteDesde() != null ? e.getVigenteDesde().toString() : null)
                .vigenteHasta(e.getVigenteHasta() != null ? e.getVigenteHasta().toString() : null)
                .activo(e.isActivo())
                .vigente(e.isVigente())
                .horariosDia(dias)
                .totalHorasNetas(fmt(totalNetos))
                .totalHorasExtra(fmt(totalExtra))
                .totalHorasBrutas(fmt(totalNetos + totalExtra))
                .tieneProgramaciones(nProg > 0)
                .build();
    }

    private String fmt(Integer min) {
        if (min == null || min == 0) return "00:00";
        return String.format("%02d:%02d", min / 60, min % 60);
    }
}