package com.idat.asistencia.model.entity;

import com.idat.asistencia.model.enums.EstadoQuincena;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "quincenas",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_quincena", columnNames = {"anio", "mes", "numero"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Quincena {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_quincena")
    private Long idQuincena;

    @Column(nullable = false)
    private Integer anio;

    @Column(nullable = false)
    private Integer mes;            // 1..12

    @Column(nullable = false)
    private Integer numero;         // 1 = primera (1–15), 2 = segunda (16–fin)

    // ── Rango calendario ─────────────────────────────────────
    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;  // día 1 o día 16

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;     // día 15 o último día del mes

    /**
     * Hora de corte para turnos nocturnos.
     * El período real inicia el día anterior a esta hora y termina
     * el día fechaFin a esta hora.
     * Por defecto: 18:00
     */
    @Column(name = "hora_corte", nullable = false)
    @Builder.Default
    private LocalTime horaCorte = LocalTime.of(18, 0);

    // ── Límites reales (calculados, incluyen lógica nocturna) ─
    // inicioReal = fechaInicio - 1 día a las horaCorte
    // finReal    = fechaFin a las horaCorte

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    @Builder.Default
    private EstadoQuincena estado = EstadoQuincena.ABIERTA;

    @Column(name = "cerrado_por")
    private Long cerradoPor;

    @Column(name = "cerrado_en")
    private LocalDateTime cerradoEn;

    @Column(name = "reabierto_por")
    private Long reabiertoPor;

    @Column(name = "reabierto_en")
    private LocalDateTime reabiertaEn;

    @Column(name = "motivo_reaper", length = 255)
    private String motivoReapertura;

    // ── Helpers ───────────────────────────────────────────────

    /**
     * Inicio real del período: el día anterior a fechaInicio a las horaCorte.
     * Ej: 1ra quincena marzo → 28/02 18:00:00
     */
    public LocalDateTime getInicioReal() {
        return fechaInicio.minusDays(1).atTime(horaCorte);
    }

    /**
     * Fin real del período: fechaFin a las horaCorte.
     * Ej: 1ra quincena marzo → 15/03 18:00:00
     */
    public LocalDateTime getFinReal() {
        return fechaFin.atTime(horaCorte);
    }

    /** Descripción legible. Ej: "1ra quincena de marzo 2026" */
    public String getDescripcion() {
        String[] meses = {"", "enero","febrero","marzo","abril","mayo","junio",
                "julio","agosto","septiembre","octubre","noviembre","diciembre"};
        return (numero == 1 ? "1ra" : "2da") + " quincena de "
                + meses[mes] + " " + anio;
    }
}