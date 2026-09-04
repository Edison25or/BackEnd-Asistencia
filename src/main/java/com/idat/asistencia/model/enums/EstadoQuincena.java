package com.idat.asistencia.model.enums;

/**
 * La reapertura es directa, en un solo paso, ejecutada por el
 * Superadministrador (RN-38). Por eso se elimina el valor
 * REAPERTURA_PENDIENTE que usaba el flujo de dos pasos del prototipo.
 */
public enum EstadoQuincena {
    ABIERTA,
    CERRADA
}
