package com.idat.asistencia.model.enums;

public enum EstadoAsistencia {
    /** Pre-registro creado, sin marcas reales aún */
    PENDIENTE,
    /** El trabajador registró entrada (y opcionalmente salida) */
    MARCADO,
    /** Los tiempos fueron calculados automáticamente por el sistema */
    CALCULADO,
    /** El supervisor revisó y validó los tiempos */
    REVISADO,
    /** Incluido en el cierre de una quincena */
    CONSOLIDADO
}