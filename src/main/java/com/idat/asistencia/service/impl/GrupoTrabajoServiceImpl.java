package com.idat.asistencia.service.impl;

import com.idat.asistencia.dto.GrupoTrabajoDTOs.*;
import com.idat.asistencia.exception.BusinessException;
import com.idat.asistencia.exception.ResourceNotFoundException;
import com.idat.asistencia.model.entity.GrupoTrabajo;
import com.idat.asistencia.model.entity.Trabajador;
import com.idat.asistencia.model.enums.EstadoTrabajador;
import com.idat.asistencia.repository.GrupoTrabajoRepository;
import com.idat.asistencia.repository.TrabajadorRepository;
import com.idat.asistencia.service.AuditoriaService;
import com.idat.asistencia.service.GrupoTrabajoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GrupoTrabajoServiceImpl implements GrupoTrabajoService {

    private final GrupoTrabajoRepository grupoRepo;
    private final TrabajadorRepository   trabajadorRepo;
    private final AuditoriaService        auditoriaService;

    // ── Listar ───────────────────────────────────────────────
    @Override
    public List<GrupoResponse> getAll() {
        return grupoRepo.findAllWithTrabajadores()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public GrupoResponse getById(Integer id) {
        return toResponse(buscarOLanzar(id));
    }

    // ── Crear ────────────────────────────────────────────────
    @Override
    @Transactional
    public GrupoResponse crear(GrupoRequest request) {
        if (grupoRepo.existsByNombre(request.getNombre()))
            throw new BusinessException("Ya existe un grupo con ese nombre.");

        GrupoTrabajo grupo = GrupoTrabajo.builder()
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .build();

        // Grupos vacíos son permitidos
        if (request.getIdsTrabajadores() != null && !request.getIdsTrabajadores().isEmpty()) {
            grupo.setTrabajadores(resolverYValidarTrabajadores(request.getIdsTrabajadores(), null));
        }

        GrupoTrabajo saved = grupoRepo.save(grupo);
        auditoriaService.registrar("grupos_trabajo", saved.getIdGrupo().longValue(), "CREAR");
        return toResponse(saved);
    }

    // ── Actualizar ───────────────────────────────────────────
    @Override
    @Transactional
    public GrupoResponse actualizar(Integer id, GrupoRequest request) {
        GrupoTrabajo grupo = buscarOLanzar(id);

        if (!grupo.getNombre().equals(request.getNombre()) &&
                grupoRepo.existsByNombre(request.getNombre()))
            throw new BusinessException("Ya existe un grupo con ese nombre.");

        grupo.setNombre(request.getNombre());
        grupo.setDescripcion(request.getDescripcion());

        if (request.getIdsTrabajadores() != null) {
            // null = limpiar todos; lista vacía = sin miembros
            grupo.setTrabajadores(
                    request.getIdsTrabajadores().isEmpty()
                            ? new HashSet<>()
                            : resolverYValidarTrabajadores(request.getIdsTrabajadores(), id)
            );
        }

        GrupoTrabajo saved = grupoRepo.save(grupo);
        auditoriaService.registrar("grupos_trabajo", saved.getIdGrupo().longValue(), "MODIFICAR");
        return toResponse(saved);
    }

    // ── Eliminar ─────────────────────────────────────────────
    @Override
    @Transactional
    public void eliminar(Integer id) {
        GrupoTrabajo g = buscarOLanzar(id);
        grupoRepo.delete(g);
        auditoriaService.registrar("grupos_trabajo", id.longValue(), "ELIMINAR");
    }

    // ── Asignar / remover individuales ────────────────────────
    @Override
    @Transactional
    public GrupoResponse asignarTrabajadores(Integer idGrupo, List<Long> idsTrabajadores) {
        GrupoTrabajo grupo = buscarOLanzar(idGrupo);
        grupo.getTrabajadores().addAll(resolverYValidarTrabajadores(idsTrabajadores, idGrupo));
        return toResponse(grupoRepo.save(grupo));
    }

    @Override
    @Transactional
    public GrupoResponse removerTrabajador(Integer idGrupo, Long idTrabajador) {
        GrupoTrabajo grupo = buscarOLanzar(idGrupo);
        grupo.getTrabajadores().removeIf(t -> t.getIdTrabajador().equals(idTrabajador));
        return toResponse(grupoRepo.save(grupo));
    }

    // ── HELPERS ──────────────────────────────────────────────

    /**
     * Resuelve y valida que ningún trabajador pertenezca ya a otro grupo.
     * @param idGrupoActual null al crear; ID del grupo en edición para excluirlo de la validación.
     */
    private Set<Trabajador> resolverYValidarTrabajadores(List<Long> ids, Integer idGrupoActual) {
        List<String> conflictos = new ArrayList<>();

        Set<Trabajador> resultado = ids.stream()
                .map(id -> trabajadorRepo.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado: " + id)))
                .filter(t -> t.getEstado() == EstadoTrabajador.ACTIVO)
                .peek(t -> {
                    Optional<GrupoTrabajo> grupoExistente = idGrupoActual == null
                            ? grupoRepo.findByTrabajadorId(t.getIdTrabajador())
                            : grupoRepo.findByTrabajadorIdExcludingGrupo(t.getIdTrabajador(), idGrupoActual);

                    grupoExistente.ifPresent(g ->
                            conflictos.add("'" + t.getPNombre() + " " + t.getAPaterno()
                                    + "' ya pertenece al grupo «" + g.getNombre() + "»")
                    );
                })
                .collect(Collectors.toCollection(HashSet::new));

        if (!conflictos.isEmpty())
            throw new BusinessException("No se puede guardar el grupo. Conflictos: " +
                    String.join("; ", conflictos));

        return resultado;
    }

    private GrupoTrabajo buscarOLanzar(Integer id) {
        return grupoRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado: " + id));
    }

    private GrupoResponse toResponse(GrupoTrabajo g) {
        List<TrabajadorResumenDTO> trabajadores = g.getTrabajadores().stream()
                .map(t -> TrabajadorResumenDTO.builder()
                        .idTrabajador(t.getIdTrabajador())
                        .nombreCompleto(t.getPNombre() + " " + t.getAPaterno() + " " + t.getAMaterno())
                        .nroDocumento(t.getNroDocumento())
                        .puestoNombre(t.getPuesto() != null ? t.getPuesto().getPuesto() : null)
                        .areaNombre(t.getPuesto() != null && t.getPuesto().getArea() != null
                                ? t.getPuesto().getArea().getArea() : null)
                        .build())
                .collect(Collectors.toList());

        return GrupoResponse.builder()
                .idGrupo(g.getIdGrupo())
                .nombre(g.getNombre())
                .descripcion(g.getDescripcion())
                .totalTrabajadores(trabajadores.size())
                .trabajadores(trabajadores)
                .build();
    }
}