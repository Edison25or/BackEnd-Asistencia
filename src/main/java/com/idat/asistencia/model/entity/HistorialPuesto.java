package com.idat.asistencia.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "historial_puestos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistorialPuesto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial_puesto")
    private Long idHistorialPuesto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_trabajador", nullable = false)
    private Trabajador trabajador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_puesto", nullable = false)
    private Puesto puesto;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    // Puede ser null si es el puesto que ocupa actualmente
    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    // Ej: "Contratación inicial", "Promoción a supervisor", "Rotación de área"
    @Column(name = "motivo_cambio", length = 100)
    private String motivoCambio;
}