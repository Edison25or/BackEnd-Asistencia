package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.Permiso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PermisoRepository extends JpaRepository<Permiso, Long> {

    /**
     * Permisos de un trabajador que cubren una fecha. Se usa al generar
     * pre-registros, para neutralizar los dias de un permiso que ya
     * estaba registrado antes de programar la semana (RN-44).
     */
    @Query("""
        SELECT p FROM Permiso p
        WHERE p.trabajador.idTrabajador = :idTrabajador
          AND :fecha BETWEEN p.fechaInicio AND p.fechaFin
    """)
    List<Permiso> findQueCubren(@Param("idTrabajador") Long idTrabajador,
                                @Param("fecha")        LocalDate fecha);

    @Query("""
        SELECT p FROM Permiso p
        JOIN FETCH p.tipoAusencia
        WHERE p.trabajador.idTrabajador = :idTrabajador
          AND p.fechaInicio <= :hasta
          AND p.fechaFin    >= :desde
        ORDER BY p.fechaInicio ASC
    """)
    List<Permiso> findPorTrabajadorYRango(@Param("idTrabajador") Long idTrabajador,
                                          @Param("desde")        LocalDate desde,
                                          @Param("hasta")        LocalDate hasta);

    boolean existsByTipoAusencia_IdTipoAusencia(Integer idTipoAusencia);
}
