package com.idat.asistencia.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

public class ConsolidadoDTOs {

    // ── Respuesta principal del consolidado ──────────────────
    @Data @Builder
    public static class ConsolidadoResponse {
        private Long   id;
        private Long   idQuincena;
        private String quincenaDescripcion;
        private Long   idTrabajador;
        private String trabajadorNombre;
        private String puestoNombre;
        private String areaNombre;

        // Horas normales
        private Integer minNormalesDia;
        private Integer minNormalesNoche;
        private String  hNormalesDia;       // formateado HH:mm
        private String  hNormalesNoche;

        // Horas extra tasa A
        private BigDecimal tasaA;
        private Integer    minExtraDiaA;
        private Integer    minExtranocheA;
        private String     hExtraDiaA;
        private String     hExtraNocheA;

        // Horas extra tasa B
        private BigDecimal tasaB;
        private Integer    minExtraDiaB;
        private Integer    minExtraNocheB;
        private String     hExtraDiaB;
        private String     hExtraNocheB;

        // Totales calculados
        private String  hTotalNormales;
        private String  hTotalExtra;
        private String  hTotalGeneral;

        // Descuentos
        private Integer minDiaDescontar;
        private Integer minNocheDescontar;

        // Informativos
        private Integer minTotalTardanza;
        private Integer diasFalta;
        private Integer diasPermiso;

        // Bolsa
        private Integer bolsaEntrada;
        private Integer bolsaAcumulada;
        private Integer bolsaConsumida;
        private Integer bolsaSalida;
        private String  hBolsaEntrada;
        private String  hBolsaSalida;

        // Bonos y observaciones (editables)
        private BigDecimal otroBono;
        private String     detalleOtroBono;
        private String     observaciones;

        // Decisiones extra
        private Integer minExtraPagados;
        private Integer minExtraABolsa;

        // Estado
        private String estado;
        private String generadoEn;
        private String cerradoEn;
    }

    // ── Request para editar campos manuales ──────────────────
    @Data
    public static class EditarConsolidadoRequest {
        private BigDecimal otroBono;
        private String     detalleOtroBono;
        private String     observaciones;
    }

    // ── Request para cerrar quincena (con decisiones de bolsa) ─
    @Data
    public static class CerrarQuincenaRequest {
        private Long   idQuincena;
        /**
         * Decisiones por trabajador. Si no se envía uno,
         * se asume que todos los extras se pagan.
         */
        private List<DecisionExtraDTO> decisiones;
    }

    @Data
    public static class DecisionExtraDTO {
        private Long    idTrabajador;
        /** Minutos que se pagan al 100% (ya incluyen sobretasa) */
        private Integer minExtraPagados;
        /** Minutos que van a la bolsa */
        private Integer minExtraABolsa;
        /** Minutos de la bolsa que se consumen esta quincena */
        private Integer bolsaConsumida;
    }

    // ── Respuesta al cerrar quincena ─────────────────────────
    @Data @Builder
    public static class CierreQuincenaResponse {
        private Long   idQuincena;
        private String descripcion;
        private int    totalTrabajadores;
        private int    consolidadosCerrados;
        private String cerradoEn;
    }

    // ── Request para reapertura ───────────────────────────────
    @Data
    public static class ReaperturaRequest {
        private Long   idQuincena;
        private String motivo;
    }

    // ── Resumen de quincena para el panel de consolidado ─────
    @Data @Builder
    public static class QuincenaConsolidadoResumenDTO {
        private Long   idQuincena;
        private String descripcion;
        private String fechaInicio;
        private String fechaFin;
        private String estado;
        private long   totalConsolidados;
        private long   pendientesRevision;   // asistencias aún no revisadas
        private boolean puedeGenerarse;      // true si todos los REVISADOS
        private boolean puedeCerrarse;
    }

    // ── Historial de bolsa de un trabajador ──────────────────
    @Data @Builder
    public static class BolsaHistorialDTO {
        private Long   idQuincena;
        private String quincenaDescripcion;
        private String fechaInicio;
        private String fechaFin;
        private Integer bolsaEntrada;
        private Integer bolsaAcumulada;
        private Integer bolsaConsumida;
        private Integer bolsaSalida;
        private String  hBolsaEntrada;
        private String  hBolsaAcumulada;
        private String  hBolsaConsumida;
        private String  hBolsaSalida;
        private String  estadoQuincena;
        // Detalle del movimiento
        private Integer minExtraPagados;
        private Integer minExtraABolsa;
    }

    // ── Respuesta completa del reporte de quincena ────────────
    @Data @Builder
    public static class ConsolidadoReporteResponse {
        private Long   idQuincena;
        private String descripcion;
        private String fechaInicio;
        private String fechaFin;
        private String estado;
        private java.util.List<ConsolidadoResponse> trabajadores;
        // Totales globales
        private String totalHNormalesDia;
        private String totalHNormalesNoche;
        private String totalHExtraDia;
        private String totalHExtraNoche;
        private String totalHGeneral;
        private Integer totalDiasFalta;
        private Integer totalDiasPermiso;
        private Integer totalTrabajadores;
    }
}