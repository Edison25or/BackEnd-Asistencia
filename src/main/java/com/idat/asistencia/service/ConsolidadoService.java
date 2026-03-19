package com.idat.asistencia.service;

import com.idat.asistencia.dto.ConsolidadoDTOs.*;
import com.idat.asistencia.dto.ConsolidadoDTOs.BolsaHistorialDTO;
import com.idat.asistencia.dto.ConsolidadoDTOs.ConsolidadoReporteResponse;
import java.util.List;

public interface ConsolidadoService {

    /** Genera el consolidado de todos los trabajadores de una quincena */
    List<ConsolidadoResponse> generarConsolidado(Long idQuincena);

    /** Lista el consolidado ya generado de una quincena */
    List<ConsolidadoResponse> getConsolidado(Long idQuincena);

    /** Obtiene el consolidado de un trabajador específico */
    ConsolidadoResponse getConsolidadoTrabajador(Long idQuincena, Long idTrabajador);

    /** Edita los campos manuales (bono, observaciones) de un consolidado */
    ConsolidadoResponse editar(Long idConsolidado, EditarConsolidadoRequest req);

    /** Cierra la quincena aplicando las decisiones de bolsa */
    CierreQuincenaResponse cerrarQuincena(CerrarQuincenaRequest req, String username);

    /** Solicita reapertura (Admin) — queda en REAPERTURA_PENDIENTE */
    void solicitarReapertura(ReaperturaRequest req);

    /** Aprueba reapertura (SuperAdmin) */
    void aprobarReapertura(Long idQuincena, String username);

    /** Lista quincenas con resumen de consolidado */
    List<QuincenaConsolidadoResumenDTO> getQuincenasConResumen();

    /** Historial de bolsa de horas de un trabajador (todas las quincenas) */
    List<BolsaHistorialDTO> getHistorialBolsa(Long idTrabajador);

    /** Reporte completo de una quincena con totales globales */
    ConsolidadoReporteResponse getReporte(Long idQuincena);
}