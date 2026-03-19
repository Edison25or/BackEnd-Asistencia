package com.idat.asistencia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class MaestroDTOs {

    // ── GÉNERO ──────────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class GeneroRequest {
        @NotBlank(message = "El nombre del género es obligatorio")
        @Size(max = 20, message = "Máximo 20 caracteres")
        private String genero;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class GeneroResponse {
        private Integer idGenero;
        private String  genero;
        private boolean activo;
    }

    // ── ÁREA ────────────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AreaRequest {
        @NotBlank(message = "El nombre del área es obligatorio")
        @Size(max = 100, message = "Máximo 100 caracteres")
        private String area;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AreaResponse {
        private Integer idArea;
        private String  area;
        private boolean activo;
    }

    // ── PUESTO ──────────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PuestoRequest {
        @NotBlank(message = "El nombre del puesto es obligatorio")
        @Size(max = 100, message = "Máximo 100 caracteres")
        private String puesto;

        @Size(max = 255)
        private String descripcionPuesto;

        @NotNull(message = "Debe seleccionar un área")
        private Integer idArea;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PuestoResponse {
        private Integer idPuesto;
        private String  puesto;
        private String  descripcionPuesto;
        private Integer idArea;
        private String  areaNombre;
        private boolean activo;
    }
}