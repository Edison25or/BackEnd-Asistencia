package com.idat.asistencia.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "programaciones_semanales")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProgramacionSemanal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_programacion")
    private Long idProgramacion;

    @Column(name = "semana_inicio", nullable = false)
    private LocalDate semanaInicio;

    @Column(name = "semana_fin", nullable = false)
    private LocalDate semanaFin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_esquema", nullable = false)
    private EsquemaHorario esquema;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_trabajador", nullable = false)
    private Trabajador trabajador;

    // ── Snapshot del grupo al momento de la asignación ──────
    // Se guarda una vez y nunca se modifica.
    // Permite reconstruir las tarjetas visuales aunque el grupo
    // haya cambiado o sido eliminado posteriormente.
    @Column(name = "grupo_id_snapshot")
    private Integer grupoIdSnapshot;

    @Column(name = "grupo_nombre_snapshot", length = 50)
    private String grupoNombreSnapshot;
}