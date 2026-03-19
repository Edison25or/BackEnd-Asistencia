package com.idat.asistencia.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Entity
@Table(name = "horarios_dia",
       uniqueConstraints = @UniqueConstraint(columnNames = {"id_esquema", "dia_semana"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HorarioDia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_horario_dia")
    private Long idHorarioDia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_esquema", nullable = false)
    private EsquemaHorario esquema;

    // LUNES=1, MARTES=2, ... DOMINGO=7
    @Column(name = "dia_semana", nullable = false)
    private Integer diaSemana;

    // Para ordenar en la UI
    @Column(name = "orden_dia", nullable = false)
    private Integer ordenDia;

    // Si es día de descanso, los demás campos son null
    @Column(name = "es_descanso", nullable = false)
    @Builder.Default
    private Boolean esDescanso = false;

    @Column(name = "hora_entrada")
    private LocalTime horaEntrada;

    // Tiempo de refrigerio en minutos
    @Column(name = "minutos_refrigerio")
    private Integer minutosRefrigerio;

    // Horas netas en minutos (ej: 11:36 = 696 minutos)
    @Column(name = "minutos_netos")
    private Integer minutosNetos;

    // Extra programado en minutos (ej: 00:24 = 24 minutos)
    @Column(name = "minutos_extra_programado")
    @Builder.Default
    private Integer minutosExtraProgramado = 0;

    // Hora de salida calculada (NO almacenada en BD)
    @Transient
    public LocalTime getHoraSalidaCalculada() {
        if (Boolean.TRUE.equals(esDescanso) || horaEntrada == null) return null;
        int totalMinutos = (minutosNetos != null ? minutosNetos : 0)
                         + (minutosRefrigerio != null ? minutosRefrigerio : 0)
                         + (minutosExtraProgramado != null ? minutosExtraProgramado : 0);
        return horaEntrada.plusMinutes(totalMinutos);
    }

    // Total de minutos trabajados en el día (netos + extra)
    @Transient
    public int getTotalMinutosDia() {
        if (Boolean.TRUE.equals(esDescanso)) return 0;
        return (minutosNetos != null ? minutosNetos : 0)
             + (minutosExtraProgramado != null ? minutosExtraProgramado : 0);
    }

    public String getNombreDia() {
        return switch (diaSemana) {
            case 1 -> "Lunes";
            case 2 -> "Martes";
            case 3 -> "Miércoles";
            case 4 -> "Jueves";
            case 5 -> "Viernes";
            case 6 -> "Sábado";
            case 7 -> "Domingo";
            default -> "—";
        };
    }
}
