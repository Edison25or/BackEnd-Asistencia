package com.idat.asistencia.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ausencia no planificada con motivo valido, registrada por el Jefe
 * (RN-29). A diferencia del permiso, no tiene limite de plazo para
 * registrarse (RN-32) y por eso carece del indicador fueraDePlazo.
 *
 * Registrada despues del cierre, no modifica el consolidado ya emitido:
 * la neutralizacion de pre-registros solo alcanza a los que no estan
 * en estado CONSOLIDADO (RN-44).
 */
@Entity
@Table(name = "faltas_justificadas",
       indexes = {
           @Index(name = "ix_falta_just_trab_rango",
                  columnList = "id_trabajador, fecha_inicio, fecha_fin")
       })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FaltaJustificada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_falta_justificada")
    private Long idFaltaJustificada;

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

    @Transient
    public boolean cubre(LocalDate fecha) {
        return fecha != null
            && !fecha.isBefore(fechaInicio)
            && !fecha.isAfter(fechaFin);
    }
}
