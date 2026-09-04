package com.idat.asistencia.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Periodo de vinculacion laboral. Un trabajador que reingresa acumula
 * varios periodos, conservando su historial (CU10).
 *
 * CAMBIO: motivoCese pasa de String libre a FK al catalogo MotivoCese
 * (RN-11). Se conserva un campo de detalle para el valor "Otro".
 */
@Entity
@Table(name = "periodos_laborales")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PeriodoLaboral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_periodo")
    private Long idPeriodo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_trabajador", nullable = false)
    private Trabajador trabajador;

    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDate fechaIngreso;

    /** null mientras el trabajador siga activo en este periodo. */
    @Column(name = "fecha_cese")
    private LocalDate fechaCese;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_motivo_cese")
    private MotivoCese motivoCese;

    /** Detalle libre cuando el motivo seleccionado es "Otro". */
    @Column(name = "detalle_motivo_cese", length = 255)
    private String detalleMotivoCese;

    @Transient
    public boolean isVigente() {
        return fechaCese == null;
    }
}
