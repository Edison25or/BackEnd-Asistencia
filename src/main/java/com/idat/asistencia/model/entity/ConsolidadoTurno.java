package com.idat.asistencia.model.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Totales de un trabajador en una quincena, para una combinacion concreta
 * de turno y condicion de feriado.
 *
 * Con dos turnos y un feriado en el periodo, un trabajador puede tener
 * hasta cuatro filas. Ejemplo:
 *
 *   Turno   Feriado   Normales   Extra
 *   Dia     No          4 320      300
 *   Noche   No            960        0
 *   Noche   Si            360        0
 *
 * IMPORTANTE: los buckets son EXCLUYENTES. Los minutos trabajados dentro
 * de un dia feriado se restan de la fila no feriado del mismo turno y se
 * suman a la fila de feriado. Contarlos en ambos lados duplicaria las
 * horas del consolidado.
 *
 * minExtra es el total de hora extra reconocida: la estructural, que se
 * reconoce automaticamente, mas la excepcional aprobada por el Jefe
 * (RN-33, RN-34). La distincion entre ambas se conserva en el registro
 * diario y en el reporte detallado, pero el consolidado la reporta como
 * un unico total.
 */
@Entity
@Table(name = "consolidado_turno",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_consol_turno",
               columnNames = {"id_consolidado", "id_turno", "es_feriado"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConsolidadoTurno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_consolidado_turno")
    private Long idConsolidadoTurno;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_consolidado", nullable = false)
    private ConsolidadoQuincena consolidado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_turno", nullable = false)
    private Turno turno;

    /** true = minutos trabajados dentro de un dia feriado (RN-41). */
    @Column(name = "es_feriado", nullable = false)
    @Builder.Default
    private boolean esFeriado = false;

    @Column(name = "min_normales", nullable = false)
    @Builder.Default
    private Integer minNormales = 0;

    /** Estructural mas excepcional aprobada. */
    @Column(name = "min_extra", nullable = false)
    @Builder.Default
    private Integer minExtra = 0;

    @Transient
    public boolean coincide(Turno otroTurno, boolean feriado) {
        if (this.esFeriado != feriado) return false;
        if (this.turno == null || otroTurno == null) return this.turno == otroTurno;
        return this.turno.getIdTurno().equals(otroTurno.getIdTurno());
    }

    @Transient
    public int getTotalMinutos() {
        return minNormales + minExtra;
    }
}
