package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.Asistencia;
import com.idat.asistencia.model.enums.EstadoAsistencia;
import com.idat.asistencia.model.enums.TipoAsistencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AsistenciaRepository extends JpaRepository<Asistencia, Long> {

    // ── Consultas existentes (compatibilidad) ─────────────────
    Optional<Asistencia> findByTrabajador_IdTrabajadorAndFecha(Long idTrabajador, LocalDate fecha);

    @Query("""
        SELECT a FROM Asistencia a
        JOIN FETCH a.trabajador t
        JOIN FETCH t.puesto p
        JOIN FETCH p.area
        WHERE a.fecha = :fecha
          AND a.ingresoReal IS NOT NULL
          AND a.salidaReal IS NULL
    """)
    List<Asistencia> findTrabajadoresEnPlanta(@Param("fecha") LocalDate fecha);

    @Query("""
        SELECT a FROM Asistencia a
        JOIN FETCH a.trabajador t
        LEFT JOIN FETCH t.puesto
        WHERE a.fecha = :fecha
    """)
    List<Asistencia> findByFecha(@Param("fecha") LocalDate fecha);

    // ── Nuevas consultas para revisión ────────────────────────

    /** Asistencias de un trabajador en un rango de fechas */
    @Query("""
        SELECT a FROM Asistencia a
        JOIN FETCH a.trabajador t
        LEFT JOIN FETCH a.esquema
        WHERE t.idTrabajador = :idTrabajador
          AND a.fecha BETWEEN :desde AND :hasta
        ORDER BY a.fecha ASC
    """)
    List<Asistencia> findByTrabajadorYRango(
            @Param("idTrabajador") Long idTrabajador,
            @Param("desde")        LocalDate desde,
            @Param("hasta")        LocalDate hasta
    );

    /** Todas las asistencias de una quincena para revisión */
    @Query("""
        SELECT a FROM Asistencia a
        JOIN FETCH a.trabajador t
        LEFT JOIN FETCH t.puesto p
        LEFT JOIN FETCH p.area
        LEFT JOIN FETCH a.esquema
        WHERE a.quincena.idQuincena = :idQuincena
        ORDER BY t.aPaterno ASC, t.pNombre ASC, a.fecha ASC
    """)
    List<Asistencia> findByQuincena(@Param("idQuincena") Long idQuincena);

    /** Asistencias de una quincena por trabajador */
    @Query("""
        SELECT a FROM Asistencia a
        WHERE a.quincena.idQuincena = :idQuincena
          AND a.trabajador.idTrabajador = :idTrabajador
        ORDER BY a.fecha ASC
    """)
    List<Asistencia> findByQuincenaYTrabajador(
            @Param("idQuincena")   Long idQuincena,
            @Param("idTrabajador") Long idTrabajador
    );

    /** Asistencias pendientes de revisión (tienen salida pero no han sido revisadas) */
    @Query("""
        SELECT a FROM Asistencia a
        JOIN FETCH a.trabajador t
        WHERE a.quincena.idQuincena = :idQuincena
          AND a.estado IN ('CALCULADO', 'MARCADO')
        ORDER BY a.fecha ASC
    """)
    List<Asistencia> findPendientesRevision(@Param("idQuincena") Long idQuincena);

    /** Contar asistencias no revisadas de una quincena */
    long countByQuincena_IdQuincenaAndEstadoIn(
            Long idQuincena, List<EstadoAsistencia> estados);

    /** Reporte de asistencias por rango de fechas con filtros opcionales */
    @Query("""
        SELECT a FROM Asistencia a
        JOIN FETCH a.trabajador t
        LEFT JOIN FETCH t.puesto p
        LEFT JOIN FETCH p.area ar
        WHERE a.fecha BETWEEN :inicio AND :fin
          AND (:idTrabajador IS NULL OR t.idTrabajador = :idTrabajador)
          AND (:idArea IS NULL OR ar.idArea = :idArea)
        ORDER BY a.fecha ASC, t.aPaterno ASC, t.pNombre ASC
    """)
    List<Asistencia> findReporte(
            @Param("inicio")       LocalDate inicio,
            @Param("fin")          LocalDate fin,
            @Param("idTrabajador") Long idTrabajador,
            @Param("idArea")       Integer idArea
    );

    /** Pre-registros existentes para evitar duplicados */
    boolean existsByTrabajador_IdTrabajadorAndFechaAndTipo(
            Long idTrabajador, LocalDate fecha, TipoAsistencia tipo);
}