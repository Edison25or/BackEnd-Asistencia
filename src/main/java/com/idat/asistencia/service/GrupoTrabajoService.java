package com.idat.asistencia.service;

import com.idat.asistencia.dto.GrupoTrabajoDTOs.*;
import java.util.List;

public interface GrupoTrabajoService {
    List<GrupoResponse> getAll();
    GrupoResponse getById(Integer id);

    /** Activos del area que aun no pertenecen a ningun grupo (RN-20, RN-21). */
    List<TrabajadorResumenDTO> getDisponibles(Integer idArea);

    GrupoResponse crear(GrupoRequest request);
    GrupoResponse actualizar(Integer id, GrupoRequest request);
    void eliminar(Integer id);
    GrupoResponse asignarTrabajadores(Integer idGrupo, List<Long> idsTrabajadores);
    GrupoResponse removerTrabajador(Integer idGrupo, Long idTrabajador);
}
