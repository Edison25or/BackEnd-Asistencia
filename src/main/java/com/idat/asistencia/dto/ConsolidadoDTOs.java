package com.idat.asistencia.dto;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.util.List;

public class ConsolidadoDTOs {

    /**
     * Consolidado de un trabajador en una quincena.
     *
     * Se retiran todos los campos de bolsa de horas, tasas de recargo y
     * bono en soles: el sistema no calcula montos (AL-01) ni tramos de
     * recargo (AL-04).
     */
    @Data @Builder
    public static class ConsolidadoResponse {
        private Long   id;
        private Long   idQuincena;
        private String quincenaDescripcion;
        private Long   idTrabajador;
        private String trabajadorNombre;
        private String puestoNombre;
        private String areaNombre;

        /** Una fila por turno y condicion de feriado. */
        private List<TotalTurnoDTO> totalesPorTurno;

        private String hTotalNormales;
        private String hTotalExtra;
        private String hTotalFeriado;
        private String hTotalGeneral;

        private Integer diasFalta;
        private Integer diasPermiso;
        private Integer diasFaltaJustificada;

        private Integer minTotalTardanza;
        private Integer minTotalSalTemprana;
        private Integer minAcumuladoVsEsperado;
        private String  hAcumuladoVsEsperado;

        private String  observaciones;
        private Integer version;
        private String  estado;
        private String  generadoEn;
        private String  cerradoEn;
    }

    /**
     * Totales de una combinacion de turno y condicion de feriado.
     *
     * Los buckets son excluyentes: los minutos de feriado se restan del
     * total normal del turno. La exportacion a Excel pivotea estas filas
     * a las columnas que Contabilidad requiera: horas dia, horas noche,
     * horas extra dia, horas extra noche, horas feriado dia y horas
     * feriado noche.
     */
    @Data @Builder
    public static class TotalTurnoDTO {
        private String  turno;
        private boolean esFeriado;
        private Integer minNormales;
        private Integer minExtra;
        private String  hNormales;
        private String  hExtra;
    }

    @Data
    public static class EditarConsolidadoRequest {
        @Size(max = 500, message = "Las observaciones no pueden exceder 500 caracteres")
        private String observaciones;
    }

    @Data @Builder
    public static class QuincenaConsolidadoResumenDTO {
        private Long    idQuincena;
        private String  descripcion;
        private String  inicio;
        private String  fin;
        private String  estado;
        private long    totalConsolidados;
        /** Registros que impiden generar el consolidado (RN-37). */
        private long    bloqueantes;
        private boolean puedeGenerarse;
    }

    @Data
    public static class ReaperturaRequest {
        @NotNull(message = "El ID de la quincena es obligatorio")
        private Long   idQuincena;

        @NotBlank(message = "El motivo de la reapertura es obligatorio")
        @Size(min = 10, max = 500,
              message = "El motivo debe tener entre 10 y 500 caracteres")
        private String motivo;
    }

    @Data @Builder
    public static class ConsolidadoReporteResponse {
        private Long   idQuincena;
        private String descripcion;
        private String inicio;
        private String fin;
        private String estado;
        private List<ConsolidadoResponse> trabajadores;

        private Integer totalTrabajadores;
        private String  totalHNormales;
        private String  totalHExtra;
        private String  totalHFeriado;
        private Integer totalDiasFalta;
        private Integer totalDiasPermiso;
        private Integer totalDiasFaltaJustificada;
    }
}
