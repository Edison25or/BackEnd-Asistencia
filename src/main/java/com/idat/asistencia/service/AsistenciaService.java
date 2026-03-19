package com.idat.asistencia.service;

import com.idat.asistencia.dto.AsistenciaDTOs.*;
import java.util.List;

public interface AsistenciaService {
    // Lector (existentes)
    MarcarAsistenciaResponse marcar(String codigo);
    List<AsistenciaResumenDTO> getTrabajadoresEnPlanta();
    List<AsistenciaResumenDTO> getAsistenciasDia();

    // Revisión
    List<AsistenciaRevisionDTO> getParaRevision(Long idQuincena);
    AsistenciaRevisionDTO validarTiempos(ValidarTiemposRequest req, String usernameRevisor);
    AsistenciaRevisionDTO registrarNoProgramada(RegistrarNoProgramadaRequest req);

    // Quincenas
    List<QuincenaResumenDTO> getQuincenas();
    QuincenaResumenDTO crearQuincena(Integer anio, Integer mes, Integer numero);
}