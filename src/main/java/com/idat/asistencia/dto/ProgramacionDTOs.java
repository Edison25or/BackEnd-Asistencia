package com.idat.asistencia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.List;

public class ProgramacionDTOs {

    @Data
    public static class ProgramacionRequest {
        @NotBlank(message = "La semana de inicio es obligatoria")
        private String  semanaInicio;

        @NotNull(message = "El esquema es obligatorio")
        private Integer idEsquema;

        private Long    idTrabajador;
    }

    @Data @Builder
    public static class ProgramacionResponse {
        private Long    idProgramacion;
        private String  semanaInicio;
        private String  semanaFin;
        private String  semanaLabel;
        private Integer idEsquema;
        private String  esquemaNombre;
        /** Turno del esquema. Determina la clasificacion de horas (RN-18). */
        private String  turnoNombre;
        private Long    idTrabajador;
        private String  trabajadorNombre;
        private String  trabajadorDocumento;
        private String  puestoNombre;
        private String  areaNombre;

        private Integer grupoIdSnapshot;
        private String  grupoNombreSnapshot;

        private boolean semanaPassada;
    }

    /**
     * Resultado de confirmar la semana.
     *
     * idQuincena pasa a ser una LISTA: una semana que cruza el corte del
     * 15 o de fin de mes genera pre-registros de DOS quincenas distintas,
     * y eso es correcto.
     */
    @Data @Builder
    public static class ConfirmarSemanaResponse {
        private String     semanaLabel;
        private int        totalTrabajadores;
        private int        preRegistrosCreados;
        private int        preRegistrosOmitidos;
        /** Cuantos de los creados caen en dia feriado (RN-41). */
        private int        preRegistrosEnFeriado;
        private String     quincenaDescripcion;
        private List<Long> idsQuincenas;
    }

    @Data @Builder
    public static class ProgramacionBulkResponse {
        private int    creados;
        private int    omitidos;
        private String semanaLabel;
    }
}
