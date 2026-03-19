package com.idat.asistencia.service;

import com.idat.asistencia.dto.GrupoTrabajoDTOs.*;
import java.util.List;

public interface GrupoTrabajoService {
    List<GrupoResponse> getAll();
    GrupoResponse getById(Integer id);
    GrupoResponse crear(GrupoRequest request);
    GrupoResponse actualizar(Integer id, GrupoRequest request);
    void eliminar(Integer id);
    GrupoResponse asignarTrabajadores(Integer idGrupo, List<Long> idsTrabajadores);
    GrupoResponse removerTrabajador(Integer idGrupo, Long idTrabajador);
}