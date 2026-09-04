package com.idat.asistencia.dto;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

public class AusenciaDTOs {

    @Data
    public static class PermisoRequest {
        @NotNull(message = "El trabajador es obligatorio")
        private Long    idTrabajador;

        @NotNull(message = "El tipo de ausencia es obligatorio")
        private Integer idTipoAusencia;

        @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$",
                message = "La fecha debe tener formato yyyy-MM-dd")
        private String  fechaInicio;

        @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$",
                message = "La fecha debe tener formato yyyy-MM-dd")
        private String  fechaFin;

        @NotBlank(message = "El comentario es obligatorio")
        @Size(max = 500)
        private String  comentario;
    }

    @Data
    public static class FaltaJustificadaRequest {
        @NotNull(message = "El trabajador es obligatorio")
        private Long    idTrabajador;

        @NotNull(message = "El tipo de ausencia es obligatorio")
        private Integer idTipoAusencia;

        @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$",
                message = "La fecha debe tener formato yyyy-MM-dd")
        private String  fechaInicio;

        @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$",
                message = "La fecha debe tener formato yyyy-MM-dd")
        private String  fechaFin;

        @NotBlank(message = "El comentario es obligatorio")
        @Size(max = 500)
        private String  comentario;
    }

    @Data @Builder
    public static class PermisoResponse {
        private Long    idPermiso;
        private Long    idTrabajador;
        private String  trabajadorNombre;
        private String  tipoAusencia;
        private String  fechaInicio;
        private String  fechaFin;
        private String  comentario;
        /** true si se registro fuera del plazo estandar (RN-30). */
        private boolean fueraDePlazo;
        private String  registradoPor;
        private String  fechaRegistro;
        /** Pre-registros que dejaron de contar como falta (RN-44). */
        private int     preRegistrosNeutralizados;
    }

    @Data @Builder
    public static class FaltaJustificadaResponse {
        private Long   idFaltaJustificada;
        private Long   idTrabajador;
        private String trabajadorNombre;
        private String tipoAusencia;
        private String fechaInicio;
        private String fechaFin;
        private String comentario;
        private String registradoPor;
        private String fechaRegistro;
        private int    preRegistrosNeutralizados;
    }
}
