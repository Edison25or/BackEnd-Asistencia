package com.idat.asistencia.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Plantilla de horario semanal, versionada hacia adelante (RN-19, RT-06).
 *
 * ============================================================
 * CAMBIOS RESPECTO DEL PROTOTIPO
 * ============================================================
 * 1. toleranciaMinutos se renombra a toleranciaTardanza y se agregan
 *    toleranciaPrevia y toleranciaPosterior (RN-17). El prototipo solo
 *    tenia una tolerancia, insuficiente para distinguir una entrada
 *    anticipada normal de una que dispara la confirmacion por doble
 *    escaneo.
 *
 * 2. Se agrega la relacion obligatoria con Turno (RN-18), que reemplaza
 *    la clasificacion por umbral horario fijo.
 */
@Entity
@Table(name = "esquemas_horario",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_esquema_version",
               columnNames = {"grupo_nombre", "version"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EsquemaHorario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_esquema")
    private Integer idEsquema;

    @Column(nullable = false, length = 80)
    private String nombre;

    /** Nombre base que agrupa todas las versiones. */
    @Column(name = "grupo_nombre", nullable = false, length = 80)
    private String grupoNombre;

    @Column(length = 200)
    private String descripcion;

    /**
     * Turno al que pertenece el esquema. Determina la clasificacion de las
     * horas en el consolidado (RN-18, RN-25).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_turno", nullable = false)
    private Turno turno;

    // ---------- Tolerancias (RN-17) ----------

    /** Minutos despues de la entrada que aun se consideran puntuales. */
    @Column(name = "tolerancia_tardanza", nullable = false)
    @Builder.Default
    private Integer toleranciaTardanza = 10;

    /** Minutos antes de la entrada considerados normales, sin hora extra. */
    @Column(name = "tolerancia_previa", nullable = false)
    @Builder.Default
    private Integer toleranciaPrevia = 15;

    /** Minutos despues de la salida considerados normales. */
    @Column(name = "tolerancia_posterior", nullable = false)
    @Builder.Default
    private Integer toleranciaPosterior = 15;

    // ---------- Versionado ----------

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    /** null = version actualmente vigente */
    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

    /** false = deshabilitado manualmente, no aparece en nuevas programaciones */
    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @OneToMany(mappedBy = "esquema", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("ordenDia ASC")
    @Builder.Default
    private List<HorarioDia> horariosDia = new ArrayList<>();

    // ---------- Helpers ----------

    @Transient
    public boolean isVigente() {
        return vigenteHasta == null;
    }

    @Transient
    public boolean isCerrado() {
        return vigenteHasta != null;
    }

    /** true si el esquema tiene los 7 dias definidos (RT-03). */
    @Transient
    public boolean isCompleto() {
        return horariosDia != null && horariosDia.size() == 7;
    }
}
