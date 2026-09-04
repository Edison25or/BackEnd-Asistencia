package com.idat.asistencia.model.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Catalogo de tipos de ausencia, usado por Permiso y FaltaJustificada.
 * Se entrega sin valores precargados (AL-03); la empresa lo completa
 * antes de que esos flujos puedan operar (DEP-04).
 *
 * Admite desactivacion aunque tenga uso historico (RN-16).
 */
@Entity
@Table(name = "tipos_ausencia")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TipoAusencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_ausencia")
    private Integer idTipoAusencia;

    @Column(nullable = false, length = 80, unique = true)
    private String nombre;

    @Column(length = 200)
    private String descripcion;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}
