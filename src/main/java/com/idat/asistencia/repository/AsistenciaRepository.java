package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.Asistencia;
import com.idat.asistencia.model.enums.EstadoAsistencia;
import com.idat.asistencia.model.enums.TipoRegistro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AsistenciaRepository extends JpaRepository<Asistencia, Long> {

    // ============================================================
    // RESOLUCION DE JORNADA AL MARCAR (CU03)
    // ============================================================
    // Reemplazan a findByTrabajador_IdTrabajadorAndFecha(), que buscaba
    // por fecha calendario y por eso no encontraba la jornada abierta de
    // un turno nocturno cuando el trabajador marcaba su salida al dia
    // siguiente.
    //
    // El orden de prioridad esta en el servicio:
    //   1. jornada ABIERTA que contenga el instante  -> es una SALIDA
    //   2. jornada PENDIENTE que lo contenga         -> es una ENTRADA
    //   3. jornada COMPLETA que lo contenga          -> marcacion adicional
    //   4. ninguna                                   -> rechazo

    /**
     * Jornada abierta (con entrada y sin salida) cuya ventana contiene el
     * instante dado. Es la que resuelve la salida del turno noche.
     *
     * Devuelve lista, no Optional: si hubiera mas de una por un dato
     * inconsistente, el servicio toma la primera en vez de reventar con
     * NonUniqueResultException, que es lo que hacia findByFechaAproximada
     * en el prototipo.
     */
    @Query("""
        SELECT a FROM Asistencia a
        JOIN FETCH a.trabajador t
        LEFT JOIN FETCH a.esquema e
        LEFT JOIN FETCH a.turno
        WHERE t.idTrabajador = :idTrabajador
          AND a.ingresoReal IS NOT NULL
          AND a.salidaReal  IS NULL
          AND :instante BETWEEN a.inicioVentana AND a.finVentana
        ORDER BY a.inicioVentana DESC
    """)
    List<Asistencia> findJornadasAbiertasEnVentana(
            @Param("idTrabajador") Long idTrabajador,
            @Param("instante")     LocalDateTime instante);

    /**
     * Jornada pendiente (sin marcacion) cuya ventana contiene el instante.
     * Si hay varias, el servicio toma la de inicioVentana mas cercano.
     */
    @Query("""
        SELECT a FROM Asistencia a
        JOIN FETCH a.trabajador t
        LEFT JOIN FETCH a.esquema e
        LEFT JOIN FETCH a.turno
        WHERE t.idTrabajador = :idTrabajador
          AND a.ingresoReal IS NULL
          AND :instante BETWEEN a.inicioVentana AND a.finVentana
        ORDER BY a.inicioVentana ASC
    """)
    List<Asistencia> findJornadasPendientesEnVentana(
            @Param("idTrabajador") Long idTrabajador,
            @Param("instante")     LocalDateTime instante);

    /** Jornada ya completa cuya ventana contiene el instante (posible doblete). */
    @Query("""
        SELECT a FROM Asistencia a
        WHERE a.trabajador.idTrabajador = :idTrabajador
          AND a.ingresoReal IS NOT NULL
          AND a.salidaReal  IS NOT NULL
          AND :instante BETWEEN a.inicioVentana AND a.finVentana
        ORDER BY a.salidaReal DESC
    """)
    List<Asistencia> findJornadasCompletasEnVentana(
            @Param("idTrabajador") Long idTrabajador,
            @Param("instante")     LocalDateTime instante);

    // Guarda anti-rebote (HU-53). Se resuelve con dos agregados y no con
    // GREATEST, que no forma parte de JPQL estandar. El servicio toma el
    // mayor de ambos.

    @Query("""
        SELECT MAX(a.ingresoReal) FROM Asistencia a
        WHERE a.trabajador.idTrabajador = :idTrabajador
    """)
    Optional<LocalDateTime> findUltimoIngresoReal(@Param("idTrabajador") Long idTrabajador);

    @Query("""
        SELECT MAX(a.salidaReal) FROM Asistencia a
        WHERE a.trabajador.idTrabajador = :idTrabajador
    """)
    Optional<LocalDateTime> findUltimaSalidaReal(@Param("idTrabajador") Long idTrabajador);

    // ============================================================
    // CIERRE DIARIO DE JORNADAS (CU29, RN-42)
    // ============================================================

    /**
     * Jornadas cuya ventana ya vencio y siguen sin resolverse.
     * Solo de quincenas abiertas: lo consolidado no se toca.
     */
    @Query("""
        SELECT a FROM Asistencia a
        JOIN FETCH a.trabajador t
        LEFT JOIN FETCH a.esquema
        LEFT JOIN FETCH a.quincena q
        WHERE a.finVentana < :ahora
          AND a.estado IN (
              com.idat.asistencia.model.enums.EstadoAsistencia.PENDIENTE,
              com.idat.asistencia.model.enums.EstadoAsistencia.MARCADO)
          AND (q IS NULL OR q.estado = com.idat.asistencia.model.enums.EstadoQuincena.ABIERTA)
        ORDER BY a.finVentana ASC
    """)
    List<Asistencia> findJornadasVencidasSinResolver(@Param("ahora") LocalDateTime ahora);

    // ============================================================
    // NEUTRALIZACION POR AUSENCIA (RN-44, HU-51)
    // ============================================================

    /**
     * Jornadas de un trabajador dentro de un rango de fechas que aun
     * pueden neutralizarse por permiso, falta justificada o feriado.
     * Excluye las consolidadas (RN-32).
     */
    @Query("""
        SELECT a FROM Asistencia a
        LEFT JOIN a.quincena q
        WHERE a.trabajador.idTrabajador = :idTrabajador
          AND a.fecha BETWEEN :desde AND :hasta
          AND a.estado <> com.idat.asistencia.model.enums.EstadoAsistencia.CONSOLIDADO
          AND (q IS NULL OR q.estado = com.idat.asistencia.model.enums.EstadoQuincena.ABIERTA)
    """)
    List<Asistencia> findNeutralizablesEnRango(
            @Param("idTrabajador") Long idTrabajador,
            @Param("desde")        LocalDate desde,
            @Param("hasta")        LocalDate hasta);

    // ============================================================
    // FERIADOS (RN-41, HU-52)
    // ============================================================

    /**
     * Jornadas que solapan con un dia calendario, para recalcular
     * minutosFeriado cuando el feriado se registra despues de ocurridas.
     *
     * El solapamiento se evalua sobre las marcaciones REALES y no sobre la
     * fecha de la jornada, porque una jornada nocturna que empieza la
     * vispera aporta minutos al feriado aunque su fecha sea el dia
     * anterior.
     */
    @Query("""
        SELECT a FROM Asistencia a
        JOIN FETCH a.trabajador
        LEFT JOIN a.quincena q
        WHERE a.ingresoReal IS NOT NULL
          AND a.salidaReal  IS NOT NULL
          AND a.ingresoReal < :finDia
          AND a.salidaReal  > :inicioDia
          AND (q IS NULL OR q.estado = com.idat.asistencia.model.enums.EstadoQuincena.ABIERTA)
    """)
    List<Asistencia> findQueSolapanDia(
            @Param("inicioDia") LocalDateTime inicioDia,
            @Param("finDia")    LocalDateTime finDia);

    /**
     * Jornadas ligadas a un permiso que ya no pueden desligarse: estan
     * consolidadas o su quincena esta cerrada.
     *
     * Se usa como guarda antes de eliminar la ausencia. Sin ella, la clave
     * foranea abortaria el borrado y el usuario veria un fallo de base de
     * datos en vez de una explicacion (RN-32).
     */
    @Query("""
        SELECT COUNT(a) FROM Asistencia a
        LEFT JOIN a.quincena q
        WHERE a.permiso.idPermiso = :idPermiso
          AND (a.estado = com.idat.asistencia.model.enums.EstadoAsistencia.CONSOLIDADO
               OR q.estado = com.idat.asistencia.model.enums.EstadoQuincena.CERRADA)
    """)
    long countBloqueadasPorPermiso(@Param("idPermiso") Long idPermiso);

    @Query("""
        SELECT COUNT(a) FROM Asistencia a
        LEFT JOIN a.quincena q
        WHERE a.faltaJustificada.idFaltaJustificada = :idFalta
          AND (a.estado = com.idat.asistencia.model.enums.EstadoAsistencia.CONSOLIDADO
               OR q.estado = com.idat.asistencia.model.enums.EstadoQuincena.CERRADA)
    """)
    long countBloqueadasPorFalta(@Param("idFalta") Long idFalta);

    /** Pre-registros sin marcar de una fecha, para marcarlos no laborables. */
    @Query("""
        SELECT a FROM Asistencia a
        LEFT JOIN a.quincena q
        WHERE a.fecha = :fecha
          AND a.ingresoReal IS NULL
          AND a.estado <> com.idat.asistencia.model.enums.EstadoAsistencia.CONSOLIDADO
          AND (q IS NULL OR q.estado = com.idat.asistencia.model.enums.EstadoQuincena.ABIERTA)
    """)
    List<Asistencia> findSinMarcarEnFecha(@Param("fecha") LocalDate fecha);

    // ============================================================
    // BANDEJA DE PENDIENTES Y CIERRE DE QUINCENA (CU20, CU21)
    // ============================================================

    @Query("""
        SELECT a FROM Asistencia a
        JOIN FETCH a.trabajador t
        LEFT JOIN FETCH t.puesto p
        LEFT JOIN FETCH p.area
        LEFT JOIN FETCH a.esquema
        LEFT JOIN FETCH a.turno
        WHERE a.quincena.idQuincena = :idQuincena
        ORDER BY t.aPaterno ASC, t.pNombre ASC, a.fecha ASC
    """)
    List<Asistencia> findByQuincena(@Param("idQuincena") Long idQuincena);

    @Query("""
        SELECT a FROM Asistencia a
        LEFT JOIN FETCH a.turno
        WHERE a.quincena.idQuincena = :idQuincena
          AND a.trabajador.idTrabajador = :idTrabajador
        ORDER BY a.fecha ASC
    """)
    List<Asistencia> findByQuincenaYTrabajador(
            @Param("idQuincena")   Long idQuincena,
            @Param("idTrabajador") Long idTrabajador);

    /**
     * Cuenta lo que BLOQUEA el cierre de la quincena (RN-37).
     *
     * El criterio del prototipo bloqueaba ante cualquier estado distinto
     * de REVISADO o CONSOLIDADO. Como ninguna jornada normal llegaba a
     * REVISADO por si sola, el cierre quedaba bloqueado siempre.
     *
     * Bloquea: PENDIENTE, MARCADO, y CALCULADO con requiereRevision.
     * NO bloquea: la falta injustificada que ya resolvio el cierre diario,
     * porque es un hecho decidido y no un pendiente. De otro modo la falta
     * de un solo trabajador impediria cerrar la quincena de los demas.
     */
    @Query("""
        SELECT COUNT(a) FROM Asistencia a
        WHERE a.quincena.idQuincena = :idQuincena
          AND (a.estado IN (
                   com.idat.asistencia.model.enums.EstadoAsistencia.PENDIENTE,
                   com.idat.asistencia.model.enums.EstadoAsistencia.MARCADO)
               OR (a.estado = com.idat.asistencia.model.enums.EstadoAsistencia.CALCULADO
                   AND a.requiereRevision = true))
    """)
    long countBloqueantes(@Param("idQuincena") Long idQuincena);

    /** Detalle de los registros que bloquean, para explicar el rechazo. */
    @Query("""
        SELECT a FROM Asistencia a
        JOIN FETCH a.trabajador t
        WHERE a.quincena.idQuincena = :idQuincena
          AND (a.estado IN (
                   com.idat.asistencia.model.enums.EstadoAsistencia.PENDIENTE,
                   com.idat.asistencia.model.enums.EstadoAsistencia.MARCADO)
               OR (a.estado = com.idat.asistencia.model.enums.EstadoAsistencia.CALCULADO
                   AND a.requiereRevision = true))
        ORDER BY t.aPaterno ASC, a.fecha ASC
    """)
    List<Asistencia> findBloqueantes(@Param("idQuincena") Long idQuincena);

    /** Registros que requieren decision humana, para la bandeja. */
    @Query("""
        SELECT a FROM Asistencia a
        JOIN FETCH a.trabajador t
        WHERE a.quincena.idQuincena = :idQuincena
          AND a.requiereRevision = true
        ORDER BY a.fecha ASC
    """)
    List<Asistencia> findRequierenRevision(@Param("idQuincena") Long idQuincena);

    long countByQuincena_IdQuincenaAndEstadoIn(
            Long idQuincena, List<EstadoAsistencia> estados);

    // ============================================================
    // PANEL DEL DIA Y REPORTES
    // ============================================================

    /**
     * Personal actualmente en planta: jornadas con entrada y sin salida.
     *
     * Ya no filtra por fecha del dia. Con dos turnos en paralelo y el
     * turno noche cruzando la medianoche (RT-09), filtrar por fecha
     * escondia las jornadas nocturnas iniciadas la vispera, que siguen
     * abiertas y cuyos trabajadores SI estan en planta.
     */
    @Query("""
        SELECT a FROM Asistencia a
        JOIN FETCH a.trabajador t
        JOIN FETCH t.puesto p
        JOIN FETCH p.area
        WHERE a.ingresoReal IS NOT NULL
          AND a.salidaReal  IS NULL
          AND a.finVentana >= :ahora
        ORDER BY a.ingresoReal ASC
    """)
    List<Asistencia> findEnPlanta(@Param("ahora") LocalDateTime ahora);

    /** Jornadas cuya ventana toca el intervalo dado (vista del dia). */
    @Query("""
        SELECT a FROM Asistencia a
        JOIN FETCH a.trabajador t
        LEFT JOIN FETCH t.puesto p
        LEFT JOIN FETCH p.area
        LEFT JOIN FETCH a.turno
        WHERE a.inicioVentana < :hasta
          AND a.finVentana    > :desde
        ORDER BY a.inicioVentana ASC
    """)
    List<Asistencia> findPorIntervalo(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    @Query("""
        SELECT a FROM Asistencia a
        JOIN FETCH a.trabajador t
        LEFT JOIN FETCH a.esquema
        LEFT JOIN FETCH a.turno
        WHERE t.idTrabajador = :idTrabajador
          AND a.fecha BETWEEN :desde AND :hasta
        ORDER BY a.fecha ASC
    """)
    List<Asistencia> findByTrabajadorYRango(
            @Param("idTrabajador") Long idTrabajador,
            @Param("desde")        LocalDate desde,
            @Param("hasta")        LocalDate hasta);

    @Query("""
        SELECT a FROM Asistencia a
        JOIN FETCH a.trabajador t
        LEFT JOIN FETCH t.puesto p
        LEFT JOIN FETCH p.area ar
        LEFT JOIN FETCH a.turno
        WHERE a.fecha BETWEEN :inicio AND :fin
          AND (:idTrabajador IS NULL OR t.idTrabajador = :idTrabajador)
          AND (:idArea IS NULL OR ar.idArea = :idArea)
        ORDER BY a.fecha ASC, t.aPaterno ASC, t.pNombre ASC
    """)
    List<Asistencia> findReporte(
            @Param("inicio")       LocalDate inicio,
            @Param("fin")          LocalDate fin,
            @Param("idTrabajador") Long idTrabajador,
            @Param("idArea")       Integer idArea);

    // ============================================================
    // GENERACION DE PRE-REGISTROS (CU14)
    // ============================================================

    /** Evita duplicar el pre-registro programado de un dia. */
    boolean existsByTrabajador_IdTrabajadorAndFechaAndTipo(
            Long idTrabajador, LocalDate fecha, TipoRegistro tipo);

    /** Marcaciones posteriores a la fecha de cese, para validar la baja (RN-10). */
    @Query("""
        SELECT COUNT(a) FROM Asistencia a
        WHERE a.trabajador.idTrabajador = :idTrabajador
          AND a.ingresoReal IS NOT NULL
          AND a.fecha > :fechaCese
    """)
    long countMarcacionesPosterioresA(
            @Param("idTrabajador") Long idTrabajador,
            @Param("fechaCese")    LocalDate fechaCese);
}
