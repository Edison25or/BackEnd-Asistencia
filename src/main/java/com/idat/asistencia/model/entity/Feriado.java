package com.idat.asistencia.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Feriado del calendario (RN-41).
 *
 * Se registra como fecha, sin hora. El computo de horas trabajadas en
 * feriado se hace por dia calendario, sobre los minutos efectivamente
 * trabajados dentro de [fecha 00:00, fecha+1 00:00), con independencia de
 * a que jornada pertenezcan.
 *
 * Ese criterio es necesario porque la planta opera dos turnos en paralelo
 * y el turno noche cruza la medianoche (RT-09): ninguna regla que atribuya
 * la jornada completa a un solo dia da el resultado correcto para todos
 * los casos.
 */
@Entity
@Table(name = "feriados")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Feriado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_feriado")
    private Integer idFeriado;

    @Column(nullable = false, unique = true)
    private LocalDate fecha;

    @Column(nullable = false, length = 120)
    private String descripcion;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por")
    private Usuario registradoPor;

    @Column(name = "fecha_registro", nullable = false)
    @Builder.Default
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    /** Inicio del dia calendario del feriado. */
    @Transient
    public LocalDateTime getInicioDia() {
        return fecha.atStartOfDay();
    }

    /** Fin exclusivo del dia calendario del feriado. */
    @Transient
    public LocalDateTime getFinDia() {
        return fecha.plusDays(1).atStartOfDay();
    }
}
