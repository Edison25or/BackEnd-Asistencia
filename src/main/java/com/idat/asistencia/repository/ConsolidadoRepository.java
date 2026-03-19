package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.ConsolidadoQuincena;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsolidadoRepository extends JpaRepository<ConsolidadoQuincena, Long> {

    Optional<ConsolidadoQuincena> findByQuincena_IdQuincenaAndTrabajador_IdTrabajador(
            Long idQuincena, Long idTrabajador);

    List<ConsolidadoQuincena> findByQuincena_IdQuincenaOrderByTrabajador_APaterno(
            Long idQuincena);

    /** Último consolidado cerrado de un trabajador (para obtener bolsa_salida) */
    @Query("""
        SELECT c FROM ConsolidadoQuincena c
        WHERE c.trabajador.idTrabajador = :idTrabajador
          AND c.estado = 'CERRADO'
          AND c.quincena.idQuincena < :idQuincenaActual
        ORDER BY c.quincena.anio DESC,
                 c.quincena.mes  DESC,
                 c.quincena.numero DESC
    """)
    List<ConsolidadoQuincena> findUltimosCerradosByTrabajador(
            @Param("idTrabajador")      Long idTrabajador,
            @Param("idQuincenaActual")  Long idQuincenaActual);

    boolean existsByQuincena_IdQuincenaAndEstado(Long idQuincena, String estado);

    long countByQuincena_IdQuincena(Long idQuincena);
}