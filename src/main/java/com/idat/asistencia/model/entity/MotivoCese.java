package com.idat.asistencia.model.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Catalogo de motivos de cese (RN-11).
 * Reemplaza el String libre motivoCese de PeriodoLaboral.
 *
 * Debe incluir un valor "Otro" para los casos no tipificados; el detalle
 * se guarda en PeriodoLaboral.detalleMotivoCese.
 */
@Entity
@Table(name = "motivos_cese")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MotivoCese {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_motivo_cese")
    private Integer idMotivoCese;

    @Column(nullable = false, length = 80, unique = true)
    private String nombre;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}
