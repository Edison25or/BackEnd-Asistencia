package com.idat.asistencia.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Fila del reporte detallado (CU21, HU-38).
 *
 * El campo estado pasa del String libre A_TIEMPO / TARDE / FALTA /
 * JUSTIFICADO al enum unico TipoRegistro. El prototipo tenia tres
 * nomenclaturas de estado incompatibles entre el reporte, la respuesta de
 * marcacion y el catalogo de la bandeja.
 *
 * La hora extra se muestra desglosada entre estructural y excepcional: es
 * el reporte que usa el Jefe para responderle a un trabajador que
 * reclama, y ahi la distincion si importa, aunque el consolidado la
 * reporte como un total unico.
 */
@Data
@Builder
public class AsistenciaReporteDTO {
    private Long    idTrabajador;
    private String  nombreCompleto;
    private String  nroDocumento;
    private String  areaNombre;
    private String  puestoNombre;
    private String  fecha;
    private String  diaSemana;
    private String  turnoNombre;
    private boolean esDiaNoLaborable;

    private String  horaEntradaProg;
    private String  horaSalidaProg;
    private String  horaEntrada;
    private String  horaSalida;

    /** Valor de TipoRegistro. */
    private String  tipo;
    private String  tipoLabel;
    private String  estado;
    private boolean requiereRevision;

    private Integer minTardanza;
    private Integer minSalTemprana;
    private Integer minutosLaborados;
    private Integer minutosFeriado;
    /** Hora extra ya acordada en el esquema (RN-34). */
    private Integer minExtraEstructural;
    /** Hora extra excepcional aprobada por el Jefe (RN-33). */
    private Integer minExtraExcepcional;

    private String  observacion;
}
