package com.idat.asistencia.dto;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

public class ParametrosDTOs {

    /** Configuracion general de asistencia (CU27). */
    @Data
    public static class ParametrosGeneralesRequest {
        @NotNull(message = "P1 es obligatorio")
        @Min(value = 0) @Max(value = 720)
        private Integer maxAnticipacionEntrada;

        @NotNull(message = "P2 es obligatorio")
        @Min(value = 0) @Max(value = 720)
        private Integer maxExcesoSalida;

        @NotNull(message = "P3 es obligatorio")
        @Min(value = 0) @Max(value = 1440)
        private Integer topeCombinado;

        @Min(value = 5) @Max(value = 120)
        private Integer ventanaConfirmacionSeg;

        @Min(value = 0) @Max(value = 119)
        private Integer intervaloAntirreboteSeg;

        private Boolean descontarRefrigerioFeriado;
    }

    @Data @Builder
    public static class ParametrosGeneralesResponse {
        private Integer maxAnticipacionEntrada;
        private Integer maxExcesoSalida;
        private Integer topeCombinado;
        private Integer ventanaConfirmacionSeg;
        private Integer intervaloAntirreboteSeg;
        private boolean descontarRefrigerioFeriado;
    }

    /** Cortes de quincena (CU26). */
    @Data
    public static class ParametrosQuincenaRequest {
        @NotNull(message = "El dia de corte es obligatorio")
        @Min(value = 1, message = "El dia de corte debe estar entre 1 y 28")
        @Max(value = 28, message = "El dia de corte debe estar entre 1 y 28, "
                + "para que exista en todos los meses")
        private Integer diaCorteIntermedio;

        @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$",
                 message = "La hora de corte debe tener formato HH:mm")
        private String horaCorte;
    }

    @Data @Builder
    public static class ParametrosQuincenaResponse {
        private Integer diaCorteIntermedio;
        private String  horaCorte;
    }
}
