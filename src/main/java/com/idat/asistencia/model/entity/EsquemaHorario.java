package com.idat.asistencia.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "esquemas_horario",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_esquema_version",
                columnNames = {"grupo_nombre", "version"}
        ))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EsquemaHorario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_esquema")
    private Integer idEsquema;

    // ── Nombre visible (puede repetirse entre versiones) ──────
    @Column(nullable = false, length = 80)
    private String nombre;

    // ── Nombre base que agrupa todas las versiones ────────────
    // Ej: "Turno Mañana" agrupa v1, v2, v3...
    @Column(name = "grupo_nombre", nullable = false, length = 80)
    private String grupoNombre;

    @Column(length = 200)
    private String descripcion;

    @Column(name = "tolerancia_minutos", nullable = false)
    @Builder.Default
    private Integer toleranciaMinutos = 10;

    // ── Versionado ────────────────────────────────────────────
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    /** null = versión actualmente vigente */
    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

    /** false = deshabilitado manualmente (ya no aparece en selectors) */
    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @OneToMany(mappedBy = "esquema", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("ordenDia ASC")
    @Builder.Default
    private List<HorarioDia> horariosDia = new ArrayList<>();

    // ── Helpers ───────────────────────────────────────────────
    public boolean isVigente() {
        return vigenteHasta == null;
    }

    public boolean isCerrado() {
        return vigenteHasta != null;
    }
}