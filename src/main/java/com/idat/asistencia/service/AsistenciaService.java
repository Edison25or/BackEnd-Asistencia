package com.idat.asistencia.service;

import com.idat.asistencia.dto.AsistenciaDTOs.*;
import java.util.List;

public interface AsistenciaService {

    /** Marcacion desde el lector de planta (CU03). */
    MarcarAsistenciaResponse marcar(String codigo);

    List<AsistenciaResumenDTO> getTrabajadoresEnPlanta();
    List<EnPlantaPublicDTO>    getEnPlantaPublica();
    List<AsistenciaResumenDTO> getAsistenciasDia();

    /** Bandeja de pendientes (CU20). */
    List<AsistenciaRevisionDTO> getParaRevision(Long idQuincena);

    /** Validar hora extra excepcional (CU18). */
    AsistenciaRevisionDTO validarTiempos(ValidarTiemposRequest req, String usernameRevisor);

    /** Corregir marcacion faltante o incompleta (CU15). */
    AsistenciaRevisionDTO corregirMarcacion(CorregirMarcacionRequest req);

    /** Registro manual por contingencia (CU19). */
    AsistenciaRevisionDTO registrarNoProgramada(RegistrarNoProgramadaRequest req);

    List<QuincenaResumenDTO> getQuincenas();

    // crearQuincena() se elimina: la quincena se autogenera al confirmar
    // la programacion semanal (RN-35, CU14).
}
