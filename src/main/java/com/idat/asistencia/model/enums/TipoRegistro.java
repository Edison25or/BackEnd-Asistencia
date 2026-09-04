package com.idat.asistencia.model.enums;

/**
 * Clasificacion de una jornada. Reemplaza al antiguo TipoAsistencia y al
 * campo estadoDiario (String libre) de la entidad Asistencia.
 *
 * PERMISO y FALTA_JUSTIFICADA salen de este enum: ahora son entidades
 * propias (Permiso, FaltaJustificada) porque cada una tiene su propia
 * regla de plazo (RN-30, RN-32).
 */
public enum TipoRegistro {

    /** Pre-registro generado al confirmar la programacion semanal. */
    PROGRAMADA,

    /** Entrada anticipada confirmada por doble escaneo. Pendiente de validar (CU18). */
    HORA_EXTRA_NO_PROGRAMADA,

    /** Marcacion sobre una jornada ya completa (doblete). El Jefe decide (RN-26). */
    NO_PROGRAMADA,

    /** Entrada sin salida al vencer la ventana. Lo genera el cierre diario (RN-42). */
    MARCACION_INCOMPLETA,

    /** Jornada vencida sin marcacion ni ausencia que la cubra (RN-42). */
    FALTA_INJUSTIFICADA,

    /** Registro manual por falla del lector o caso individual (CU19). */
    CONTINGENCIA
}
