package com.idat.asistencia.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ausencia planificada, registrada por el Jefe a nombre del trabajador
 * (RN-29). El trabajador no la solicita en el sistema: la acuerda de
 * palabra y el Jefe la formaliza.
 *
 * Se separa de FaltaJustificada porque tienen reglas de plazo distintas:
 * el permiso se registra con una a dos semanas de anticipacion y fuera de
 * ese plazo se acepta con advertencia (RN-30); la falta justificada no
 * tiene limite (RN-32).
 *
 * En el prototipo ambos colapsaban en TipoAsistencia.PERMISO, sin fechas
 * propias ni registro de quien lo autorizo.
 */
@Entity
@Table(name = "permisos",
       indexes = {
           @Index(name = "ix_permiso_trab_rango",
                  columnList = "id_trabajador, fecha_inicio, fecha_fin")
       })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Permiso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_permiso")
    private Long idPermiso;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_trabajador", nullable = false)
    private Trabajador trabajador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_ausencia", nullable = false)
    private TipoAusencia tipoAusencia;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    /** Motivo obligatorio (RN-02). */
    @Column(nullable = false, length = 500)
    private String comentario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por")
    private Usuario registradoPor;

    @Column(name = "fecha_registro", nullable = false)
    @Builder.Default
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    /**
     * true si se registro con menos de una semana de anticipacion o mas de
     * dos. No bloquea el registro: lo marca como excepcion visible (RN-30).
     */
    @Column(name = "fuera_de_plazo", nullable = false)
    @Builder.Default
    private boolean fueraDePlazo = false;

    /** true si la fecha indicada cae dentro del rango del permiso. */
    @Transient
    public boolean cubre(LocalDate fecha) {
        return fecha != null
            && !fecha.isBefore(fechaInicio)
            && !fecha.isAfter(fechaFin);
    }
}
