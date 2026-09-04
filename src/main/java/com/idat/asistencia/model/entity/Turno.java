package com.idat.asistencia.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

/**
 * Catalogo de turnos (RN-18).
 *
 * Reemplaza la clasificacion por umbral horario fijo 19:00-05:00 que el
 * prototipo tenia duplicada en AsistenciaServiceImpl.esNocturno() y en
 * ProgramacionSemanalServiceImpl.esNocturnoHora().
 *
 * El turno de una jornada se toma del esquema programado, nunca de la hora
 * real de marcacion (RN-25). horaInicio y horaFin son informativos: sirven
 * para describir el turno en la interfaz, no para clasificar marcaciones.
 */
@Entity
@Table(name = "turnos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Turno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_turno")
    private Integer idTurno;

    @Column(nullable = false, length = 40, unique = true)
    private String nombre;

    @Column(name = "hora_inicio")
    private LocalTime horaInicio;

    @Column(name = "hora_fin")
    private LocalTime horaFin;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    /** true si el turno cruza la medianoche (ej. 22:00 a 06:00). Informativo. */
    @Transient
    public boolean isCruzaMedianoche() {
        if (horaInicio == null || horaFin == null) return false;
        return horaFin.isBefore(horaInicio);
    }
}
