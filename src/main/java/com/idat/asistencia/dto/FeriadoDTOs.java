package com.idat.asistencia.dto;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.util.List;

public class FeriadoDTOs {

    @Data
    public static class FeriadoRequest {
        @NotBlank(message = "La fecha es obligatoria")
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$",
                 message = "La fecha debe tener formato yyyy-MM-dd")
        private String fecha;

        @NotBlank(message = "La descripcion es obligatoria")
        @Size(max = 120)
        private String descripcion;
    }

    @Data @Builder
    public static class FeriadoResponse {
        private Integer idFeriado;
        private String  fecha;
        private String  descripcion;
        private boolean activo;
        private String  registradoPor;
    }

    /**
     * Impacto del registro de un feriado sobre los registros existentes.
     *
     * Se devuelve tanto en la vista previa como al confirmar. Con dos
     * turnos en paralelo, saber a que jornadas alcanza no es evidente: la
     * nocturna de la vispera aporta minutos aunque su fecha sea el dia
     * anterior.
     */
    @Data @Builder
    public static class ImpactoFeriadoResponse {
        private Integer idFeriado;
        private String  fecha;
        /** Jornadas con minutos dentro del dia feriado. */
        private int     jornadasConMinutos;
        /** Pre-registros sin marcar que quedan como no laborables. */
        private int     preRegistrosSinMarcar;
        private List<DetalleImpactoDTO> detalle;
    }

    @Data @Builder
    public static class DetalleImpactoDTO {
        private Long   idAsistencia;
        private String trabajador;
        private String fechaJornada;
        private String turno;
        private int    minutosEnFeriado;
    }
}
