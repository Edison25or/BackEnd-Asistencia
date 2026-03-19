package com.idat.asistencia.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "consolidado_quincena",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_consol", columnNames = {"id_quincena", "id_trabajador"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConsolidadoQuincena {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_quincena", nullable = false)
    private Quincena quincena;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_trabajador", nullable = false)
    private Trabajador trabajador;

    // ── Horas normales ────────────────────────────────────────
    @Column(name = "min_normales_dia",   nullable = false) @Builder.Default
    private Integer minNormalesDia   = 0;
    @Column(name = "min_normales_noche", nullable = false) @Builder.Default
    private Integer minNormalesNoche = 0;

    // ── Horas extra tasa A ────────────────────────────────────
    @Column(name = "tasa_a",            nullable = false) @Builder.Default
    private BigDecimal tasaA       = BigDecimal.valueOf(25.00);
    @Column(name = "min_extra_dia_a",   nullable = false) @Builder.Default
    private Integer minExtraDiaA   = 0;
    @Column(name = "min_extra_noche_a", nullable = false) @Builder.Default
    private Integer minExtranocheA = 0;

    // ── Horas extra tasa B ────────────────────────────────────
    @Column(name = "tasa_b",            nullable = false) @Builder.Default
    private BigDecimal tasaB       = BigDecimal.valueOf(35.00);
    @Column(name = "min_extra_dia_b",   nullable = false) @Builder.Default
    private Integer minExtraDiaB   = 0;
    @Column(name = "min_extra_noche_b", nullable = false) @Builder.Default
    private Integer minExtraNocheB = 0;

    // ── Descuentos por faltas ─────────────────────────────────
    @Column(name = "min_dia_descontar",   nullable = false) @Builder.Default
    private Integer minDiaDescontar   = 0;
    @Column(name = "min_noche_descontar", nullable = false) @Builder.Default
    private Integer minNocheDescontar = 0;

    // ── Informativos ──────────────────────────────────────────
    @Column(name = "min_total_tardanza",    nullable = false) @Builder.Default
    private Integer minTotalTardanza   = 0;
    @Column(name = "min_total_sal_temprana",nullable = false) @Builder.Default
    private Integer minTotalSalTemprana = 0;
    @Column(name = "dias_falta",   nullable = false) @Builder.Default
    private Integer diasFalta   = 0;
    @Column(name = "dias_permiso", nullable = false) @Builder.Default
    private Integer diasPermiso = 0;

    // ── Bolsa de horas ────────────────────────────────────────
    /** Saldo que llega de la quincena anterior (solo lectura al generar) */
    @Column(name = "bolsa_entrada", nullable = false) @Builder.Default
    private Integer bolsaEntrada = 0;

    /**
     * Minutos extra que el usuario decidió acumular en la bolsa
     * en lugar de pagar. Se setea al cerrar la quincena.
     */
    @Column(name = "bolsa_acumulada", nullable = false) @Builder.Default
    private Integer bolsaAcumulada = 0;

    /**
     * Minutos que se consumieron de la bolsa esta quincena
     * (por descanso compensatorio o compensación de falta recuperable).
     */
    @Column(name = "bolsa_consumida", nullable = false) @Builder.Default
    private Integer bolsaConsumida = 0;

    /**
     * bolsaSalida = bolsaEntrada + bolsaAcumulada - bolsaConsumida.
     * Se recalcula antes de cerrar. Puede ser negativo si hay deuda.
     */
    @Column(name = "bolsa_salida", nullable = false) @Builder.Default
    private Integer bolsaSalida = 0;

    // ── Bonos (en soles, ingreso manual) ─────────────────────
    @Column(name = "otro_bono", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal otroBono = BigDecimal.ZERO;

    @Column(name = "detalle_otro_bono", length = 255)
    private String detalleOtroBono;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    // ── Decisiones sobre extra al cierre ──────────────────────
    @Column(name = "min_extra_pagados",  nullable = false) @Builder.Default
    private Integer minExtraPagados = 0;
    @Column(name = "min_extra_a_bolsa",  nullable = false) @Builder.Default
    private Integer minExtraABolsa  = 0;

    // ── Estado y auditoría ────────────────────────────────────
    @Column(nullable = false, length = 20) @Builder.Default
    private String estado = "BORRADOR";

    @Column(name = "generado_en", nullable = false) @Builder.Default
    private LocalDateTime generadoEn = LocalDateTime.now();

    @Column(name = "generado_por")
    private Long generadoPor;

    @Column(name = "cerrado_en")
    private LocalDateTime cerradoEn;

    @Column(name = "cerrado_por")
    private Long cerradoPor;

    // ── Helpers ───────────────────────────────────────────────
    public int getTotalExtraMinutos() {
        return minExtraDiaA + minExtranocheA + minExtraDiaB + minExtraNocheB;
    }

    public int getTotalNormalesMinutos() {
        return minNormalesDia + minNormalesNoche;
    }

    public void recalcularBolsaSalida() {
        this.bolsaSalida = bolsaEntrada + bolsaAcumulada - bolsaConsumida;
    }
}