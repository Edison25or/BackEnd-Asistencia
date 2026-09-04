package com.idat.asistencia.dto;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

/** Catalogos incorporados en esta version (CU24). */
public class CatalogoDTOs {

    @Data
    public static class TurnoRequest {
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 40)
        private String nombre;

        @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$",
                 message = "La hora debe tener formato HH:mm")
        private String horaInicio;

        @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$",
                 message = "La hora debe tener formato HH:mm")
        private String horaFin;
    }

    @Data @Builder
    public static class TurnoResponse {
        private Integer idTurno;
        private String  nombre;
        private String  horaInicio;
        private String  horaFin;
        /** true si el turno cruza la medianoche. Informativo. */
        private boolean cruzaMedianoche;
        private boolean activo;
    }

    @Data
    public static class CatalogoSimpleRequest {
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 80)
        private String nombre;

        @Size(max = 200)
        private String descripcion;
    }

    @Data @Builder
    public static class CatalogoSimpleResponse {
        private Integer id;
        private String  nombre;
        private String  descripcion;
        private boolean activo;
    }
}
