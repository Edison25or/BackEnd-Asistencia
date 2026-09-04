package com.idat.asistencia.model.entity;

import com.idat.asistencia.model.enums.EstadoAsistencia;
import com.idat.asistencia.model.enums.ResultadoValidacion;
import com.idat.asistencia.model.enums.TipoRegistro;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Una fila representa una JORNADA completa: se crea como pre-registro al
 * confirmarse la programacion semanal (CU14) y se completa con las
 * marcaciones reales (CU03).
 *
 * ============================================================
 * CAMBIO DE FONDO RESPECTO DEL PROTOTIPO
 * ============================================================
 * Los cuatro campos de hora pasan de LocalTime a LocalDateTime (RT-08).
 *
 * Con LocalTime, Duration.between(salidaReal, ingresoReal) devuelve un
 * valor NEGATIVO en cuanto la jornada cruza la medianoche, de modo que el
 * turno noche calculaba horas totales negativas. Y la busqueda del
 * pre-registro por "fecha = hoy" no encontraba la jornada abierta cuando
 * el trabajador marcaba su salida al dia calendario siguiente.
 *
 * La jornada ahora se resuelve por VENTANA HORARIA (inicioVentana,
 * finVentana), no por fecha calendario. El campo fecha se conserva como
 * fecha de negocio de la jornada, igual a la fecha de la entrada
 * programada (RN-24), y se usa para atribuir turno, quincena y ausencias.
 *
 * ============================================================
 * CAMPOS ELIMINADOS
 * ============================================================
 * - esNocturno: clasificaba por umbral fijo 19:00-05:00. Lo reemplaza la
 *   relacion con el catalogo Turno (RN-18).
 * - estadoDiario: String libre con valores A_TIEMPO / TARDE / NO_PROGRAMADO
 *   que convivia con otras dos nomenclaturas incompatibles. Lo reemplaza
 *   el enum unico TipoRegistro.
 *
 * ============================================================
 * NOTA SOBRE LA CONSTRAINT UNICA
 * ============================================================
 * El prototipo declaraba UNIQUE (id_trabajador, fecha, tipo). Esa
 * restriccion impide registrar dos filas NO_PROGRAMADA el mismo dia, que
 * es justamente como se conservan las marcaciones multiples sin forzar
 * una interpretacion automatica (RN-26). Se elimina y se reemplaza por
 * indices no unicos de consulta.
 */
@Entity
@Table(name = "asistencias",
       indexes = {
           @Index(name = "ix_asist_trab_ventana",
                  columnList = "id_trabajador, inicio_ventana, fin_ventana"),
           @Index(name = "ix_asist_trab_fecha",   columnList = "id_trabajador, fecha"),
           @Index(name = "ix_asist_quincena",     columnList = "id_quincena, estado"),
           @Index(name = "ix_asist_cierre_diario",columnList = "estado, fin_ventana")
       })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Asistencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_asistencia")
    private Long idAsistencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_trabajador", nullable = false)
    private Trabajador trabajador;

    /**
     * Fecha de negocio de la jornada = fecha de la entrada programada
     * (RN-24). Una jornada nocturna que entra el 27 y sale el 28 tiene
     * fecha = 27. NO se usa para localizar la jornada al marcar; para eso
     * estan inicioVentana y finVentana.
     */
    @Column(nullable = false)
    private LocalDate fecha;

    // ---------- Clasificacion ----------

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private TipoRegistro tipo = TipoRegistro.PROGRAMADA;

    /** PENDIENTE -> MARCADO -> CALCULADO -> REVISADO -> CONSOLIDADO */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    @Builder.Default
    private EstadoAsistencia estado = EstadoAsistencia.PENDIENTE;

    /**
     * true si la jornada necesita una decision humana antes de poder
     * consolidarse.
     *
     * Sin este indicador, el estado CALCULADO no tenia transicion
     * automatica a REVISADO y el cierre de quincena quedaba bloqueado de
     * forma permanente: una jornada normal nunca llegaba a REVISADO porque
     * no habia nada que revisar, pero el criterio de bloqueo la contaba
     * igual como pendiente.
     *
     * Se activa en: HORA_EXTRA_NO_PROGRAMADA, NO_PROGRAMADA,
     * MARCACION_INCOMPLETA y salidas que exceden la tolerancia posterior.
     * NO se activa por tardanza ni por salida temprana, que son hechos ya
     * calculados y no decisiones pendientes.
     */
    @Column(name = "requiere_revision", nullable = false)
    @Builder.Default
    private boolean requiereRevision = false;

    // ---------- Relaciones ----------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_esquema")
    private EsquemaHorario esquema;

    /**
     * Turno de la jornada. Se toma del esquema programado, nunca de la
     * hora real de marcacion (RN-25). En jornadas sin esquema
     * (NO_PROGRAMADA, CONTINGENCIA) lo asigna manualmente quien resuelve
     * el registro; no se infiere.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_turno")
    private Turno turno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_programacion")
    private ProgramacionSemanal programacion;

    /** Se resuelve por la hora de entrada programada, no por la fecha. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_quincena")
    private Quincena quincena;

    /**
     * Ausencia que neutraliza este pre-registro (RN-44). Si esta presente,
     * el cierre diario no genera falta injustificada.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_permiso")
    private Permiso permiso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_falta_justificada")
    private FaltaJustificada faltaJustificada;

    /**
     * true si la fecha de la jornada es feriado. Evita que el cierre
     * diario reporte falta cuando nadie marco (RN-42).
     *
     * Es distinto de minutosFeriado: esta bandera se ancla a la fecha de
     * la jornada, mientras que el computo de minutos se hace por dia
     * calendario (RN-41).
     */
    @Column(name = "es_dia_no_laborable", nullable = false)
    @Builder.Default
    private boolean esDiaNoLaborable = false;

    // ---------- Ventana de resolucion de la jornada ----------

    /**
     * inicioVentana = ingresoProg - max(toleranciaPrevia, P1)
     * finVentana    = salidaProg  + max(toleranciaPosterior, P2)
     *
     * Persistidos e indexados. Al escanear, la jornada se localiza por
     * ventana: primero una jornada ABIERTA (con entrada y sin salida) que
     * contenga el instante actual, y en su defecto una PENDIENTE.
     */
    @Column(name = "inicio_ventana")
    private LocalDateTime inicioVentana;

    @Column(name = "fin_ventana")
    private LocalDateTime finVentana;

    // ---------- Programado (copiado del horario del dia) ----------

    @Column(name = "ingreso_prog")
    private LocalDateTime ingresoProg;

    @Column(name = "salida_prog")
    private LocalDateTime salidaProg;

    @Column(name = "min_refrigerio_prog")
    private Integer minRefrigerioProg;

    @Column(name = "min_netos_prog")
    private Integer minNetosProg;

    /** Hora extra estructural: se reconoce sin validacion (RN-34). */
    @Column(name = "min_extra_prog")
    private Integer minExtraProg;

    // ---------- Real (marcaciones del lector o registro manual) ----------

    @Column(name = "ingreso_real")
    private LocalDateTime ingresoReal;

    @Column(name = "salida_real")
    private LocalDateTime salidaReal;

    // ---------- Calculado ----------

    /** Minutos que llego antes del ingreso programado. */
    @Column(name = "min_prev_ing_prog")
    private Integer minPrevIngProg;

    /** Minutos que permanecio despues de la salida programada. */
    @Column(name = "min_post_sal_prog")
    private Integer minPostSalProg;

    /** Minutos de tardanza. Descuenta siempre, sin validacion. */
    @Column(name = "min_tardanza")
    private Integer minTardanza;

    /** Minutos que salio antes de lo programado. Descuenta siempre. */
    @Column(name = "min_sal_temprana")
    private Integer minSalTemprana;

    /** Minutos efectivamente trabajados en la jornada. */
    @Column(name = "min_horas_totales")
    private Integer minHorasTotales;

    /**
     * Minutos de esta jornada que cayeron dentro de un dia feriado
     * (RN-41). Se calcula por solapamiento con el dia calendario del
     * feriado, no por la fecha de la jornada.
     *
     * Con un feriado el sabado 28, turno dia 06:00-14:00 y turno noche
     * 22:00-06:00, el resultado es:
     *   noche que entra el 27  ->  360 min
     *   dia   que entra el 28  ->  480 min
     *   noche que entra el 28  ->  120 min
     *
     * Estos minutos son EXCLUYENTES respecto de los normales: en el
     * consolidado se restan del total del turno y se suman a la fila de
     * feriado. Contarlos en ambos lados duplicaria las horas.
     */
    @Column(name = "minutos_feriado", nullable = false)
    @Builder.Default
    private Integer minutosFeriado = 0;

    // ---------- Validacion manual ----------

    @Column(name = "val_min_prev_ing", nullable = false)
    @Builder.Default
    private Integer valMinPrevIng = 0;

    @Column(name = "val_min_post_sal", nullable = false)
    @Builder.Default
    private Integer valMinPostSal = 0;

    /** Resultado de la validacion de hora extra excepcional (CU18). */
    @Enumerated(EnumType.STRING)
    @Column(name = "resultado_validacion", length = 15)
    private ResultadoValidacion resultadoValidacion;

    /** Quien reviso. Nulo cuando la revision fue automatica. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revisado_por")
    private Usuario revisadoPor;

    @Column(name = "revisado_en")
    private LocalDateTime revisadoEn;

    /** Quien creo el registro manualmente (CU19). Distinto de revisadoPor. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por")
    private Usuario registradoPor;

    @Column(name = "fecha_registro")
    private LocalDateTime fechaRegistro;

    @Column(length = 500)
    private String observacion;

    // ============================================================
    // Helpers de estado
    // ============================================================

    /** Jornada con entrada registrada y sin salida. */
    @Transient
    public boolean isAbierta() {
        return ingresoReal != null && salidaReal == null;
    }

    /** Jornada con ambos extremos registrados. */
    @Transient
    public boolean isCompleta() {
        return ingresoReal != null && salidaReal != null;
    }

    /** true si el instante cae dentro de la ventana de resolucion. */
    @Transient
    public boolean contieneEnVentana(LocalDateTime instante) {
        return inicioVentana != null && finVentana != null
            && !instante.isBefore(inicioVentana)
            && !instante.isAfter(finVentana);
    }

    /** true si esta ausencia esta cubierta por permiso, falta o feriado. */
    @Transient
    public boolean tieneAusenciaJustificada() {
        return permiso != null || faltaJustificada != null || esDiaNoLaborable;
    }

    /**
     * Recalcula la ventana a partir de los tiempos programados.
     * Debe invocarse tras fijar ingresoProg y salidaProg.
     *
     * @param toleranciaPrevia    minutos antes de la entrada considerados normales
     * @param toleranciaPosterior minutos despues de la salida considerados normales
     * @param p1                  maxima anticipacion de entrada configurada
     * @param p2                  maximo exceso de salida configurado
     */
    public void recalcularVentana(int toleranciaPrevia, int toleranciaPosterior,
                                  int p1, int p2) {
        if (ingresoProg == null || salidaProg == null) return;
        this.inicioVentana = ingresoProg.minusMinutes(Math.max(toleranciaPrevia, p1));
        this.finVentana    = salidaProg.plusMinutes(Math.max(toleranciaPosterior, p2));
    }

    // ============================================================
    // Recalculo de tiempos
    // ============================================================

    /**
     * Recalcula los campos derivados a partir de las marcaciones reales y
     * los tiempos programados.
     *
     * Sobre LocalDateTime todas las diferencias son correctas aunque la
     * jornada cruce la medianoche, que es lo que la version anterior no
     * podia hacer.
     *
     * No toca minutosFeriado: ese calculo depende del catalogo de feriados
     * y se hace en el servicio (ver FeriadoService).
     *
     * No fuerza el estado. La transicion a CALCULADO o REVISADO la decide
     * el servicio segun requiereRevision, para no pisar un estado REVISADO
     * ya asignado cuando se revalidan tiempos.
     */
    public void recalcularTiempos() {
        if (ingresoReal == null || salidaReal == null) return;

        // Minutos antes / despues de lo programado
        if (ingresoProg != null) {
            this.minPrevIngProg = ingresoReal.isBefore(ingresoProg)
                    ? (int) Duration.between(ingresoReal, ingresoProg).toMinutes() : 0;
            this.minTardanza = ingresoReal.isAfter(ingresoProg)
                    ? (int) Duration.between(ingresoProg, ingresoReal).toMinutes() : 0;
        } else {
            this.minPrevIngProg = 0;
            this.minTardanza    = 0;
        }

        if (salidaProg != null) {
            this.minPostSalProg = salidaReal.isAfter(salidaProg)
                    ? (int) Duration.between(salidaProg, salidaReal).toMinutes() : 0;
            this.minSalTemprana = salidaReal.isBefore(salidaProg)
                    ? (int) Duration.between(salidaReal, salidaProg).toMinutes() : 0;
        } else {
            this.minPostSalProg = 0;
            this.minSalTemprana = 0;
        }

        // Duracion real en planta, menos refrigerio programado
        int duracion   = (int) Duration.between(ingresoReal, salidaReal).toMinutes();
        int refrigerio = minRefrigerioProg != null ? minRefrigerioProg : 0;

        // El tiempo fuera de lo programado que NO fue validado no se cuenta
        int prevNoValidado = Math.max(0, minPrevIngProg - nz(valMinPrevIng));
        int postNoValidado = Math.max(0, minPostSalProg - nz(valMinPostSal));

        this.minHorasTotales = Math.max(0,
                duracion - refrigerio - prevNoValidado - postNoValidado);
    }

    private static int nz(Integer v) {
        return v != null ? v : 0;
    }
}
