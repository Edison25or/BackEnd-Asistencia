package com.idat.asistencia.model.entity;

import com.idat.asistencia.model.enums.EstadoConsolidado;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Consolidado de una quincena para un trabajador. Es el artefacto que se
 * entrega a Contabilidad para calcular el pago (CU21).
 *
 * ============================================================
 * REDISENO COMPLETO RESPECTO DEL PROTOTIPO
 * ============================================================
 * Se elimina todo el subsistema de bolsa de horas (entrada, acumulada,
 * consumida, salida), los tramos de recargo Tasa A del 25 por ciento y
 * Tasa B del 35 por ciento, el bono en soles con su detalle, los minutos
 * a descontar y la constante TOPE_TASA_A_MIN.
 *
 * Contradicen el alcance: el sistema no calcula montos de pago (AL-01) ni
 * tramos de recargo porcentual (AL-04). Contabilidad hace ese calculo
 * fuera del sistema, con la informacion del consolidado exportado
 * (DEP-05).
 *
 * Los totales por turno salen a la tabla hija ConsolidadoTurno. Con
 * columnas fijas, agregar el desglose de feriado habria llevado el
 * consolidado de seis a diez columnas, y un tercer turno habria requerido
 * migrar la tabla. Con filas, un turno adicional solo agrega registros.
 *
 * Aqui quedan unicamente los datos que se cuentan por dia y no por turno.
 */
@Entity
@Table(name = "consolidado_quincena",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_consol", columnNames = {"id_quincena", "id_trabajador", "version"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConsolidadoQuincena {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_quincena", nullable = false)
    private Quincena quincena;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_trabajador", nullable = false)
    private Trabajador trabajador;

    /**
     * Totales por combinacion de turno y condicion de feriado.
     * Ver ConsolidadoTurno.
     */
    @OneToMany(mappedBy = "consolidado", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<ConsolidadoTurno> totalesPorTurno = new ArrayList<>();

    // ---------- Conteos por dia ----------

    @Column(name = "dias_falta", nullable = false)
    @Builder.Default
    private Integer diasFalta = 0;

    @Column(name = "dias_permiso", nullable = false)
    @Builder.Default
    private Integer diasPermiso = 0;

    @Column(name = "dias_falta_justificada", nullable = false)
    @Builder.Default
    private Integer diasFaltaJustificada = 0;

    // ---------- Informativos ----------

    @Column(name = "min_total_tardanza", nullable = false)
    @Builder.Default
    private Integer minTotalTardanza = 0;

    @Column(name = "min_total_sal_temprana", nullable = false)
    @Builder.Default
    private Integer minTotalSalTemprana = 0;

    /** Minutos trabajados menos minutos esperados del periodo. */
    @Column(name = "min_acumulado_vs_esperado", nullable = false)
    @Builder.Default
    private Integer minAcumuladoVsEsperado = 0;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    // ---------- Estado y trazabilidad ----------

    /**
     * Se incrementa al regenerar el consolidado tras una reapertura.
     * La version anterior queda en estado REEMPLAZADO, de modo que ambas
     * son trazables (RN-38).
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoConsolidado estado = EstadoConsolidado.BORRADOR;

    @Column(name = "generado_en", nullable = false)
    @Builder.Default
    private LocalDateTime generadoEn = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generado_por")
    private Usuario generadoPor;

    @Column(name = "cerrado_en")
    private LocalDateTime cerradoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cerrado_por")
    private Usuario cerradoPor;

    // ---------- Helpers ----------

    /** Agrega o actualiza la fila del turno y condicion de feriado dados. */
    public ConsolidadoTurno acumular(Turno turno, boolean esFeriado,
                                     int minNormales, int minExtra) {
        ConsolidadoTurno fila = totalesPorTurno.stream()
                .filter(f -> f.coincide(turno, esFeriado))
                .findFirst()
                .orElseGet(() -> {
                    ConsolidadoTurno nueva = ConsolidadoTurno.builder()
                            .consolidado(this)
                            .turno(turno)
                            .esFeriado(esFeriado)
                            .build();
                    totalesPorTurno.add(nueva);
                    return nueva;
                });
        fila.setMinNormales(fila.getMinNormales() + minNormales);
        fila.setMinExtra(fila.getMinExtra() + minExtra);
        return fila;
    }

    @Transient
    public int getTotalNormalesMinutos() {
        return totalesPorTurno.stream().mapToInt(ConsolidadoTurno::getMinNormales).sum();
    }

    @Transient
    public int getTotalExtraMinutos() {
        return totalesPorTurno.stream().mapToInt(ConsolidadoTurno::getMinExtra).sum();
    }

    /** Minutos trabajados en dia feriado, en todos los turnos. */
    @Transient
    public int getTotalFeriadoMinutos() {
        return totalesPorTurno.stream()
                .filter(ConsolidadoTurno::isEsFeriado)
                .mapToInt(f -> f.getMinNormales() + f.getMinExtra())
                .sum();
    }
}
