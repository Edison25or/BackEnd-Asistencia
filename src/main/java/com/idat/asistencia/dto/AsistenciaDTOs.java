package com.idat.asistencia.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

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
        private Long    idAsistencia;
        private Integer valMinPrevIng;   // minutos previos que se validan
        private Integer valMinPostSal;   // minutos posteriores que se validan
        private String  observacion;
        private String  tipo;            // puede cambiar FALTA → PERMISO
    }

    // ── Request para crear asistencia no programada ───────────
    @Data
    public static class RegistrarNoProgramadaRequest {
        private Long   idTrabajador;
        private String fecha;        // yyyy-MM-dd
        private String ingresoReal;  // HH:mm
        private String salidaReal;   // HH:mm
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