package com.idat.asistencia.service;

/**
 * Resolucion automatica de jornadas vencidas (CU29, RN-42).
 * Es el unico productor de los tipos FALTA_INJUSTIFICADA y
 * MARCACION_INCOMPLETA.
 */
public interface CierreDiarioService {

    /**
     * Procesa las jornadas cuya ventana ya vencio sin completarse.
     * Idempotente: puede ejecutarse tantas veces como haga falta.
     *
     * @return resumen de lo resuelto
     */
    ResultadoCierre ejecutar();

    record ResultadoCierre(int faltasInjustificadas,
                           int marcacionesIncompletas,
                           int cubiertasPorAusencia,
                           int total) {}
}
