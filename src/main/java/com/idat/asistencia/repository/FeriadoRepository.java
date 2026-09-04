package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.Feriado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FeriadoRepository extends JpaRepository<Feriado, Integer> {

    Optional<Feriado> findByFechaAndActivoTrue(LocalDate fecha);

    boolean existsByFechaAndActivoTrue(LocalDate fecha);

    List<Feriado> findByActivoTrueOrderByFechaDesc();

    /**
     * Feriados activos que solapan un intervalo. Se usa para calcular
     * minutosFeriado de una jornada: se pasan ingresoReal y salidaReal, y
     * devuelve los dias calendario con los que la jornada se cruza.
     *
     * Una jornada nocturna de 22:00 a 06:00 puede tocar dos dias, de modo
     * que la consulta devuelve lista y no un unico valor.
     */
    @Query("""
        SELECT f FROM Feriado f
        WHERE f.activo = true
          AND f.fecha BETWEEN :desde AND :hasta
        ORDER BY f.fecha ASC
    """)
    List<Feriado> findActivosEnRango(@Param("desde") LocalDate desde,
                                     @Param("hasta") LocalDate hasta);
}
