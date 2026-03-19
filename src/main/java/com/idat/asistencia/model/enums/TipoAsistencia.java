package com.idat.asistencia.model.enums;

public enum TipoAsistencia {
    /** Generada por el pre-registro al confirmar programación semanal */
    PROGRAMADA,
    /** Ingresada manualmente para trabajo fuera del horario asignado */
    NO_PROGRAMADA,
    /** Ausencia sin justificación — afecta el consolidado */
    FALTA,
    /** Ausencia justificada o recuperable — no descuenta directamente */
    PERMISO
}