package com.idat.asistencia.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import java.util.List;

public class ProgramacionDTOs {

    @Data
    public static class ProgramacionRequest {
        @NotNull(message = "La fecha de inicio (sábado) es obligatoria")
        private String semanaInicio;

        @NotNull(message = "El esquema es obligatorio")
        private Integer idEsquema;

        private Long       idTrabajador;
        private List<Long> idsTrabajadores;
    }

    @Data
    @Builder
    public static class ProgramacionResponse {
        private Long    idProgramacion;
        private String  semanaInicio;
        private String  semanaFin;
        private String  semanaLabel;
        private Integer idEsquema;
        private String  esquemaNombre;
        private Long    idTrabajador;
        private String  trabajadorNombre;
        private String  trabajadorDocumento;
        private String  puestoNombre;
        private String  areaNombre;

        // Snapshot del grupo — para reconstrucción visual de tarjetas
        private Integer grupoIdSnapshot;
        private String  grupoNombreSnapshot;

        // Indica si la semana ya pasó — el frontend lo usa para
        // mostrar la vista en solo lectura (sin botón quitar)
        private boolean semanaPassada;
    }

    // ── Respuesta al confirmar semana (genera pre-registros) ──
    @Data @Builder
    public static class ConfirmarSemanaResponse {
        private String semanaLabel;
        private int    totalTrabajadores;
        private int    preRegistrosCreados;
        private int    preRegistrosOmitidos;
        private String quincenaDescripcion;
        private Long   idQuincena;
    }

    @Data
    @Builder
    public static class ProgramacionBulkResponse {
        private int    creados;
        private int    omitidos;
        private String semanaLabel;
    }
}