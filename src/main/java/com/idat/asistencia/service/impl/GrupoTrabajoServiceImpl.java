package com.idat.asistencia.service.impl;

import com.idat.asistencia.dto.GrupoTrabajoDTOs.*;
import com.idat.asistencia.exception.BusinessException;
import com.idat.asistencia.exception.ResourceNotFoundException;
import com.idat.asistencia.model.entity.Area;
import com.idat.asistencia.model.entity.GrupoTrabajo;
import com.idat.asistencia.model.entity.Trabajador;
import com.idat.asistencia.repository.AreaRepository;
import com.idat.asistencia.repository.GrupoTrabajoRepository;
import com.idat.asistencia.repository.TrabajadorRepository;
import com.idat.asistencia.service.AuditoriaService;
import com.idat.asistencia.service.GrupoTrabajoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Grupos de trabajo (CU13).
 *
 * ============================================================
 * QUE CAMBIA
 * ============================================================
 * 1. La pertenencia vive ahora en Trabajador.grupoTrabajo, no en una
 *    coleccion de muchos a muchos. Con el modelo anterior, que un
 *    trabajador estuviera en un solo grupo (RN-21) solo podia garantizarse
 *    recorriendo todos los grupos por codigo; ahora es estructuralmente
 *    imposible que este en dos.
 *
 * 2. Se agrega la validacion de area unica (RN-20), que faltaba por
 *    completo: el prototipo comprobaba que nadie estuviera en dos grupos,
 *    pero no que todos los miembros fueran de la misma area.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GrupoTrabajoServiceImpl implements GrupoTrabajoService {

    private final GrupoTrabajoRepository grupoRepo;
    private final TrabajadorRepository   trabajadorRepo;
    private final AreaRepository         areaRepo;
    private final AuditoriaService       auditoria;

    private static final String TABLA = "grupos_trabajo";

    @Override
    public List<GrupoResponse> getAll() {
        return grupoRepo.findAll().stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public GrupoResponse getById(Integer id) {
        return toResponse(buscarOLanzar(id));
    }

    /** Candidatos para armar un grupo: activos, del area, y sin grupo. */
    @Override
    public List<TrabajadorResumenDTO> getDisponibles(Integer idArea) {
        return trabajadorRepo.findDisponiblesParaGrupo(idArea).stream()
                .map(this::toResumen).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public GrupoResponse crear(GrupoRequest request) {
        if (grupoRepo.existsByNombreIgnoreCase(request.getNombre()))
            throw new BusinessException("Ya existe un grupo con ese nombre.");

        Area area = areaRepo.findById(request.getIdArea())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Area no encontrada: " + request.getIdArea()));

        GrupoTrabajo grupo = grupoRepo.save(GrupoTrabajo.builder()
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .area(area)
                .build());

        // Los grupos vacios estan permitidos
        if (request.getIdsTrabajadores() != null && !request.getIdsTrabajadores().isEmpty())
            asignar(grupo, request.getIdsTrabajadores());

        auditoria.registrar(TABLA, grupo.getIdGrupo().longValue(), "CREAR");
        return toResponse(grupo);
    }

    @Override
    @Transactional
    public GrupoResponse actualizar(Integer id, GrupoRequest request) {
        GrupoTrabajo grupo = buscarOLanzar(id);

        if (!grupo.getNombre().equalsIgnoreCase(request.getNombre())
                && grupoRepo.existsByNombreIgnoreCase(request.getNombre()))
            throw new BusinessException("Ya existe un grupo con ese nombre.");

        grupo.setNombre(request.getNombre());
        grupo.setDescripcion(request.getDescripcion());

        // Cambiar el area de un grupo con miembros dejaria integrantes de
        // un area distinta, en contra de RN-20.
        if (request.getIdArea() != null
                && !request.getIdArea().equals(grupo.getArea().getIdArea())) {

            long miembros = trabajadorRepo.countByGrupoTrabajo_IdGrupo(id);
            if (miembros > 0)
                throw new BusinessException(
                        "No se puede cambiar el area de un grupo con " + miembros
                                + " integrante(s). Quitalos primero.");

            grupo.setArea(areaRepo.findById(request.getIdArea())
                    .orElseThrow(() -> new ResourceNotFoundException("Area no encontrada.")));
        }

        grupoRepo.save(grupo);

        if (request.getIdsTrabajadores() != null) {
            // Se limpia y se reasigna: la lista enviada es la composicion
            // completa, no un incremento.
            for (Trabajador t : trabajadorRepo.findByGrupoTrabajo_IdGrupo(id)) {
                t.setGrupoTrabajo(null);
                trabajadorRepo.save(t);
            }
            if (!request.getIdsTrabajadores().isEmpty())
                asignar(grupo, request.getIdsTrabajadores());
        }

        auditoria.registrar(TABLA, grupo.getIdGrupo().longValue(), "MODIFICAR");
        return toResponse(grupo);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        GrupoTrabajo g = buscarOLanzar(id);

        // Los miembros quedan disponibles para otro grupo (CU13, FA3)
        for (Trabajador t : trabajadorRepo.findByGrupoTrabajo_IdGrupo(id)) {
            t.setGrupoTrabajo(null);
            trabajadorRepo.save(t);
        }

        grupoRepo.delete(g);
        auditoria.registrar(TABLA, id.longValue(), "ELIMINAR");
    }

    @Override
    @Transactional
    public GrupoResponse asignarTrabajadores(Integer idGrupo, List<Long> ids) {
        GrupoTrabajo grupo = buscarOLanzar(idGrupo);
        asignar(grupo, ids);
        return toResponse(grupo);
    }

    @Override
    @Transactional
    public GrupoResponse removerTrabajador(Integer idGrupo, Long idTrabajador) {
        GrupoTrabajo grupo = buscarOLanzar(idGrupo);

        Trabajador t = trabajadorRepo.findById(idTrabajador)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado."));

        if (t.getGrupoTrabajo() == null
                || !t.getGrupoTrabajo().getIdGrupo().equals(idGrupo))
            throw new BusinessException("El trabajador no pertenece a este grupo.");

        t.setGrupoTrabajo(null);
        trabajadorRepo.save(t);

        return toResponse(grupo);
    }

    // ---------- Helpers ----------

    /**
     * Asigna trabajadores validando area unica (RN-20) y pertenencia
     * unica (RN-21). Ambas comprobaciones corren en el servidor: filtrar
     * la lista en la interfaz no impide llamar al endpoint directamente.
     */
    private void asignar(GrupoTrabajo grupo, List<Long> ids) {
        List<String> conflictos = new ArrayList<>();
        List<Trabajador> aGuardar = new ArrayList<>();

        for (Long id : ids) {
            Trabajador t = trabajadorRepo.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Trabajador no encontrado: " + id));

            if (!t.isActivo()) {
                conflictos.add("'" + t.getNombreCompleto() + "' no esta activo");
                continue;
            }

            // RN-20: area unica del grupo
            Area areaTrab = t.getArea();
            if (areaTrab == null
                    || !areaTrab.getIdArea().equals(grupo.getArea().getIdArea())) {
                conflictos.add("'" + t.getNombreCompleto() + "' pertenece al area "
                        + (areaTrab != null ? areaTrab.getArea() : "(sin area)")
                        + " y el grupo es del area " + grupo.getArea().getArea());
                continue;
            }

            // RN-21: un trabajador, un grupo
            if (t.getGrupoTrabajo() != null
                    && !t.getGrupoTrabajo().getIdGrupo().equals(grupo.getIdGrupo())) {
                conflictos.add("'" + t.getNombreCompleto() + "' ya pertenece al grupo "
                        + t.getGrupoTrabajo().getNombre());
                continue;
            }

            t.setGrupoTrabajo(grupo);
            aGuardar.add(t);
        }

        if (!conflictos.isEmpty())
            throw new BusinessException("No se puede guardar el grupo. Conflictos: "
                    + String.join("; ", conflictos));

        trabajadorRepo.saveAll(aGuardar);
    }

    private GrupoTrabajo buscarOLanzar(Integer id) {
        return grupoRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado: " + id));
    }

    private TrabajadorResumenDTO toResumen(Trabajador t) {
        return TrabajadorResumenDTO.builder()
                .idTrabajador(t.getIdTrabajador())
                .nombreCompleto(t.getNombreCompleto())
                .nroDocumento(t.getNroDocumento())
                .puestoNombre(t.getPuesto() != null ? t.getPuesto().getPuesto() : null)
                .areaNombre(t.getArea() != null ? t.getArea().getArea() : null)
                .grupoActual(t.getGrupoTrabajo() != null
                        ? t.getGrupoTrabajo().getNombre() : null)
                .build();
    }

    private GrupoResponse toResponse(GrupoTrabajo g) {
        List<TrabajadorResumenDTO> miembros =
                trabajadorRepo.findByGrupoTrabajo_IdGrupo(g.getIdGrupo())
                        .stream().map(this::toResumen).collect(Collectors.toList());

        return GrupoResponse.builder()
                .idGrupo(g.getIdGrupo())
                .nombre(g.getNombre())
                .descripcion(g.getDescripcion())
                .idArea(g.getArea().getIdArea())
                .areaNombre(g.getArea().getArea())
                .totalTrabajadores(miembros.size())
                .trabajadores(miembros)
                .build();
    }
}
