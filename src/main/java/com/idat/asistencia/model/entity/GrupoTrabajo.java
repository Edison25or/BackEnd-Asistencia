package com.idat.asistencia.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "grupos_trabajo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrupoTrabajo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_grupo")
    private Integer idGrupo;

    @Column(nullable = false, length = 50, unique = true)
    private String nombre; // Ej: "Equipo Alpha"

    @Column(length = 150)
    private String descripcion;

    // Relación muchos a muchos con Trabajador
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "grupo_trabajadores",
        joinColumns = @JoinColumn(name = "id_grupo"),
        inverseJoinColumns = @JoinColumn(name = "id_trabajador")
    )
    @Builder.Default
    private Set<Trabajador> trabajadores = new HashSet<>();
}
