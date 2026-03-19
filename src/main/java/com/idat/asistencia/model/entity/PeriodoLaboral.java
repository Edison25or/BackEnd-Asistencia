package com.idat.asistencia.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "periodos_laborales")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeriodoLaboral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_periodo")
    private Long idPeriodo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_trabajador", nullable = false)
    private Trabajador trabajador;

    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDate fechaIngreso;

    // Puede ser null si el trabajador sigue activo en este periodo
    @Column(name = "fecha_cese")
    private LocalDate fechaCese;

    // Ej: "Fin de contrato", "Renuncia voluntaria", "Despido"
    @Column(name = "motivo_cese", length = 100)
    private String motivoCese;
}