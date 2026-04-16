package com.idat.asistencia.dto;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

public class AsistenciaDTOs {

    // ── Respuesta al marcar (compatible con lo existente) ─────
    @Data @Builder
    public static class MarcarAsistenciaResponse {
        private Long   idTrabajador;
        private String nombreCompleto;
        private String accion;        // ENTRADA | SALIDA
        private String hora;
        private String estado;
        private String estadoDiario;  // A_TIEMPO | TARDE | NO_PROGRAMADO
        private String puestoNombre;
        // Nuevos
        private String tipo;          // PROGRAMADA | NO_PROGRAMADA
        private String ingresoProg;   // hora programada de ingreso
        private Integer minTardanza;  // minutos de tardanza si aplica
    }

    // ── Resumen del día / en planta ───────────────────────────
    @Data @Builder
    public static class AsistenciaResumenDTO {
        private Long   idAsistencia;
        private Long   idTrabajador;
        private String nombreCompleto;
        private String nroDocumento;
        private String puestoNombre;
        private String areaNombre;
        private String fecha;
        private String horaEntrada;   // = ingresoReal
        private String horaSalida;    // = salidaReal
        private String estado;
        private String tipo;
    }

    // ── Resumen público para pantalla de marcado (kiosco) ────
    // No expone IDs ni documentos — seguro para endpoint público
    @Data @Builder
    public static class EnPlantaPublicDTO {
        private String nombreCompleto;
        private String puestoNombre;
        private String areaNombre;
        private String horaEntrada;
    }

    // ── Detalle completo para formulario de revisión ──────────
    @Data @Builder
    public static class AsistenciaRevisionDTO {
        private Long   idAsistencia;
        private Long   idTrabajador;
        private String nombreCompleto;
        private String nroDocumento;
        private String puestoNombre;
        private String areaNombre;
        private String fecha;
        private String tipo;
        private String estado;
        private boolean esNocturno;

        // Programado
        private String  ingresoProg;
        private String  salidaProg;
        private Integer minRefrigerioProg;
        private Integer minNetosProg;
        private Integer minExtraProg;

        // Real
        private String ingresoReal;
        private String salidaReal;

        // Calculado
        private Integer minPrevIngProg;   // llegó antes
        private Integer minPostSalProg;   // salió después
        private Integer minTardanza;
        private Integer minSalTemprana;
        private Integer minHorasTotales;

        // Validación
        private Integer valMinPrevIng;
        private Integer valMinPostSal;
        private String  revisadoPor;
        private String  revisadoEn;
        private String  observacion;

        // UI: color del indicador de tiempo no validado
        private String colorPrev;   // gris/amarillo-palido/amarillo/naranja
        private String colorPost;
    }

    // ── Request para validar tiempos desde el frontend ────────
    @Data
    public static class ValidarTiemposRequest {
        @NotNull(message = "El ID de asistencia es obligatorio")
        private Long    idAsistencia;

        @Min(value = 0, message = "Los minutos no pueden ser negativos")
        @Max(value = 720, message = "Los minutos no pueden superar las 12 horas")
        private Integer valMinPrevIng;

        @Min(value = 0, message = "Los minutos no pueden ser negativos")
        @Max(value = 720, message = "Los minutos no pueden superar las 12 horas")
        private Integer valMinPostSal;

        @Size(max = 500, message = "La observación no puede exceder 500 caracteres")
        private String  observacion;

        @Pattern(regexp = "^$|^(FALTA|PERMISO|PROGRAMADA|NO_PROGRAMADA)$",
                message = "Tipo de asistencia inválido")
        private String  tipo;
    }

    // ── Request para crear asistencia no programada ───────────
    @Data
    public static class RegistrarNoProgramadaRequest {
        @NotNull(message = "El ID del trabajador es obligatorio")
        private Long   idTrabajador;

        @NotBlank(message = "La fecha es obligatoria")
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$",
                message = "La fecha debe tener formato yyyy-MM-dd")
        private String fecha;

        @NotBlank(message = "La hora de ingreso es obligatoria")
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$",
                message = "La hora de ingreso debe tener formato HH:mm")
        private String ingresoReal;

        @NotBlank(message = "La hora de salida es obligatoria")
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$",
                message = "La hora de salida debe tener formato HH:mm")
        private String salidaReal;

        @Size(max = 500, message = "La observación no puede exceder 500 caracteres")
        private String observacion;
    }

    // ── Resumen de quincena (para lista de selección) ─────────
    @Data @Builder
    public static class QuincenaResumenDTO {
        private Long   idQuincena;
        private String descripcion;  // "1ra quincena de marzo 2026"
        private String fechaInicio;
        private String fechaFin;
        private String inicioReal;   // con lógica de hora de corte
        private String finReal;
        private String estado;
        private long   totalAsistencias;
        private long   pendientesRevision;
    }
}