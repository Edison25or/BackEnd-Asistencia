package com.idat.asistencia.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "generos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Genero {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_genero")
    private Integer idGenero;

    @Column(nullable = false, length = 20, unique = true)
    private String genero;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}