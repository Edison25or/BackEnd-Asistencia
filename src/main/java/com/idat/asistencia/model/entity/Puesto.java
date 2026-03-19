package com.idat.asistencia.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "puestos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Puesto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_puesto")
    private Integer idPuesto;

    @Column(nullable = false, length = 100)
    private String puesto;

    @Column(name = "descripcion_puesto", length = 255)
    private String descripcionPuesto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_area", nullable = false)
    private Area area;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}