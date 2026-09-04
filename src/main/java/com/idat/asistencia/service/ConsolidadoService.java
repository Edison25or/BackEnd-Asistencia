package com.idat.asistencia.service;

import com.idat.asistencia.dto.ConsolidadoDTOs.*;
import java.util.List;

public interface ConsolidadoService {

    /** Genera el consolidado y cierra la quincena (CU21, RN-36). */
    List<ConsolidadoResponse> generarConsolidado(Long idQuincena);

    List<ConsolidadoResponse> getConsolidado(Long idQuincena);

    ConsolidadoResponse getConsolidadoTrabajador(Long idQuincena, Long idTrabajador);

    /**
     * Calcula el consolidado de la quincena SIN persistirlo ni cerrarla.
     *
     * Permite revisar el resultado antes de ejecutar una operacion que
     * cierra el periodo de forma irreversible salvo reapertura (RN-36).
     * No rechaza por pendientes de revision, ya que su proposito es
     * mostrar como va quedando el periodo mientras se resuelven.
     */
    List<ConsolidadoResponse> previsualizar(Long idQuincena);

    ConsolidadoResponse editar(Long idConsolidado, EditarConsolidadoRequest req);

    List<QuincenaConsolidadoResumenDTO> getQuincenasConResumen();

    ConsolidadoReporteResponse getReporte(Long idQuincena);

    /**
     * Reapertura directa por el Superadministrador (RN-38).
     * Reemplaza a solicitarReapertura() y aprobarReapertura(), que
     * implementaban un flujo de dos pasos no contemplado en el analisis.
     */
    void reabrirQuincena(ReaperturaRequest req);
}
