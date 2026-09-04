package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.FaltaJustificada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FaltaJustificadaRepository extends JpaRepository<FaltaJustificada, Long> {

    @Query("""
        SELECT f FROM FaltaJustificada f
        WHERE f.trabajador.idTrabajador = :idTrabajador
          AND :fecha BETWEEN f.fechaInicio AND f.fechaFin
    """)
    List<FaltaJustificada> findQueCubren(@Param("idTrabajador") Long idTrabajador,
                                         @Param("fecha")        LocalDate fecha);

    @Query("""
        SELECT f FROM FaltaJustificada f
        JOIN FETCH f.tipoAusencia
        WHERE f.trabajador.idTrabajador = :idTrabajador
          AND f.fechaInicio <= :hasta
          AND f.fechaFin    >= :desde
        ORDER BY f.fechaInicio ASC
    """)
    List<FaltaJustificada> findPorTrabajadorYRango(
            @Param("idTrabajador") Long idTrabajador,
            @Param("desde")        LocalDate desde,
            @Param("hasta")        LocalDate hasta);

    boolean existsByTipoAusencia_IdTipoAusencia(Integer idTipoAusencia);
}
