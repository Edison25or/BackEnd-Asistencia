package com.idat.asistencia.model.entity;

import com.idat.asistencia.model.enums.EstadoAsistencia;
import com.idat.asistencia.model.enums.TipoAsistencia;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "asistencias",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"id_trabajador", "fecha", "tipo"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Asistencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_asistencia")
    private Long idAsistencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_trabajador", nullable = false)
    private Trabajador trabajador;

    /** Fecha del turno (fecha del ingreso programado o real) */
    @Column(nullable = false)
    private LocalDate fecha;

    // ── Clasificación ─────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TipoAsistencia tipo = TipoAsistencia.PROGRAMADA;

    /**
     * Estado del workflow de revisión.
     * PENDIENTE → MARCADO → CALCULADO → REVISADO → CONSOLIDADO
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    @Builder.Default
    private EstadoAsistencia estado = EstadoAsistencia.PENDIENTE;

    /**
     * Estado resultado del día (compatibilidad con sistema existente).
     * A_TIEMPO | TARDE | FALTA | JUSTIFICADO
     * Se calcula al registrar ingreso y se preserva para reportes diarios.
     */
    @Column(name = "estado_diario", length = 20)
    private String estadoDiario;

    // ── Relaciones ────────────────────────────────────────────
    /** Esquema horario vigente ese día */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_esquema")
    private EsquemaHorario esquema;

    /** Programación semanal de la que proviene el pre-registro */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_programacion")
    private ProgramacionSemanal programacion;

    /** Quincena a la que pertenece esta asistencia */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_quincena")
    private Quincena quincena;

    /**
     * true si la hora de ingreso del esquema está entre 19:00 y 05:00.
     * Determina si las horas se clasifican como diurnas o nocturnas.
     */
    @Column(name = "es_nocturno", nullable = false)
    @Builder.Default
    private boolean esNocturno = false;

    // ── PROGRAMADO (datos del esquema para ese día) ───────────
    @Column(name = "ingreso_prog")
    private LocalTime ingresoProg;

    @Column(name = "salida_prog")
    private LocalTime salidaProg;

    /** Minutos de refrigerio programados */
    @Column(name = "min_refrigerio_prog")
    private Integer minRefrigerioProg;

    /** Minutos netos de trabajo programados */
    @Column(name = "min_netos_prog")
    private Integer minNetosProg;

    /** Minutos extra programados */
    @Column(name = "min_extra_prog")
    private Integer minExtraProg;

    // ── REAL (marcas del dispositivo o ingreso manual) ────────
    @Column(name = "ingreso_real")
    private LocalTime ingresoReal;

    @Column(name = "salida_real")
    private LocalTime salidaReal;

    // ── CALCULADO (automático al registrar salida_real) ───────

    /**
     * Minutos que llegó ANTES del inicio programado.
     * Ej: ingreso_prog=07:00, ingreso_real=06:00 → 60 min.
     * Requiere validación del supervisor para contar como horas trabajadas.
     */
    @Column(name = "min_prev_ing_prog")
    private Integer minPrevIngProg;

    /**
     * Minutos que permaneció DESPUÉS del fin programado.
     * Ej: salida_prog=17:00, salida_real=18:00 → 60 min.
     * Requiere validación del supervisor para contar como horas trabajadas.
     */
    @Column(name = "min_post_sal_prog")
    private Integer minPostSalProg;

    /**
     * Minutos de tardanza (llegó tarde).
     * Ej: ingreso_prog=07:00, ingreso_real=07:45 → 45 min.
     * Siempre descuenta de horas_totales, sin validación.
     */
    @Column(name = "min_tardanza")
    private Integer minTardanza;

    /**
     * Minutos que salió ANTES del fin programado.
     * Ej: salida_prog=17:00, salida_real=15:30 → 90 min.
     * Siempre descuenta de horas_totales, sin validación.
     */
    @Column(name = "min_sal_temprana")
    private Integer minSalTemprana;

    /**
     * Horas totales trabajadas (resultado final).
     * Fórmula:
     *   (salida_real – ingreso_real – min_refrigerio_prog)
     *   – val_min_prev_ing – val_min_post_sal
     *   (los tiempos no validados = 0, los validados se suman)
     *
     * Nota: tardanza y salida temprana ya están implícitas porque
     * se calcula sobre las marcas reales.
     */
    @Column(name = "min_horas_totales")
    private Integer minHorasTotales;

    // ── VALIDACIÓN (decisión manual del supervisor) ───────────

    /**
     * Minutos previos al ingreso programado que el supervisor valida.
     * Si no valida: 0. Si valida: = min_prev_ing_prog.
     */
    @Column(name = "val_min_prev_ing", nullable = false)
    @Builder.Default
    private Integer valMinPrevIng = 0;

    /**
     * Minutos posteriores a la salida programada que el supervisor valida.
     * Si no valida: 0. Si valida: = min_post_sal_prog.
     */
    @Column(name = "val_min_post_sal", nullable = false)
    @Builder.Default
    private Integer valMinPostSal = 0;

    /** Usuario que realizó la revisión */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revisado_por")
    private Usuario revisadoPor;

    @Column(name = "revisado_en")
    private LocalDateTime revisadoEn;

    @Column(length = 300)
    private String observacion;  // era 150, ampliado para revisión

    // ── Helper: calcular y actualizar tiempos ─────────────────
    /**
     * Recalcula todos los campos calculados a partir de las marcas reales
     * y los datos programados. Debe llamarse tras setear ingresoReal y salidaReal.
     */
    public void recalcularTiempos() {
        if (ingresoReal == null || salidaReal == null
                || ingresoProg == null || salidaProg == null) return;

        // Minutos antes del ingreso programado (llegó antes)
        int prevIng = 0;
        if (ingresoReal.isBefore(ingresoProg)) {
            prevIng = (int) java.time.Duration.between(ingresoReal, ingresoProg).toMinutes();
        }
        this.minPrevIngProg = prevIng;

        // Minutos después de la salida programada (salió después)
        int postSal = 0;
        if (salidaReal.isAfter(salidaProg)) {
            postSal = (int) java.time.Duration.between(salidaProg, salidaReal).toMinutes();
        }
        this.minPostSalProg = postSal;

        // Minutos de tardanza (llegó tarde)
        int tardanza = 0;
        if (ingresoReal.isAfter(ingresoProg)) {
            tardanza = (int) java.time.Duration.between(ingresoProg, ingresoReal).toMinutes();
        }
        this.minTardanza = tardanza;

        // Minutos de salida temprana (salió antes)
        int salTemp = 0;
        if (salidaReal.isBefore(salidaProg)) {
            salTemp = (int) java.time.Duration.between(salidaReal, salidaProg).toMinutes();
        }
        this.minSalTemprana = salTemp;

        // Horas totales:
        // (salida_real – ingreso_real) = duración total en planta
        // – refrigerio programado
        // Los tiempos no validados (val=0) no se suman → se excluyen implícitamente
        // porque la fórmula trabaja sobre marcas reales y los val solo
        // se usan para determinar qué parte extra contar
        int duracionTotal = (int) java.time.Duration.between(ingresoReal, salidaReal).toMinutes();
        int refrigerio    = minRefrigerioProg != null ? minRefrigerioProg : 0;

        // Los min previos NO validados se descuentan (no deben contar)
        // Los min previos SI validados SÍ cuentan (ya están en duracionTotal)
        int prevNoValidado  = Math.max(0, prevIng  - valMinPrevIng);
        int postNoValidado  = Math.max(0, postSal  - valMinPostSal);

        this.minHorasTotales = duracionTotal - refrigerio - prevNoValidado - postNoValidado;
        this.estado = EstadoAsistencia.CALCULADO;
    }
}