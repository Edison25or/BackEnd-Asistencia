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

    /**
     * Consolidado vigente de un trabajador: el que NO fue reemplazado por
     * una regeneracion posterior tras una reapertura (RN-38).
     */
    @Query("""
        SELECT c FROM ConsolidadoQuincena c
        JOIN FETCH c.trabajador t
        LEFT JOIN FETCH c.totalesPorTurno
        WHERE c.quincena.idQuincena = :idQuincena
          AND t.idTrabajador = :idTrabajador
          AND c.estado <> com.idat.asistencia.model.enums.EstadoConsolidado.REEMPLAZADO
    """)
    Optional<ConsolidadoQuincena> findVigentePorQuincenaYTrabajador(
            @Param("idQuincena")   Long idQuincena,
            @Param("idTrabajador") Long idTrabajador);

    @Query("""
        SELECT DISTINCT c FROM ConsolidadoQuincena c
        JOIN FETCH c.trabajador t
        LEFT JOIN FETCH t.puesto p
        LEFT JOIN FETCH p.area
        LEFT JOIN FETCH c.totalesPorTurno
        WHERE c.quincena.idQuincena = :idQuincena
          AND c.estado <> com.idat.asistencia.model.enums.EstadoConsolidado.REEMPLAZADO
        ORDER BY t.aPaterno ASC, t.aMaterno ASC, t.pNombre ASC
    """)
    List<ConsolidadoQuincena> findVigentesPorQuincena(@Param("idQuincena") Long idQuincena);

    /** Todas las versiones, incluidas las reemplazadas. Para trazabilidad. */
    @Query("""
        SELECT c FROM ConsolidadoQuincena c
        WHERE c.quincena.idQuincena = :idQuincena
          AND c.trabajador.idTrabajador = :idTrabajador
        ORDER BY c.version DESC
    """)
    List<ConsolidadoQuincena> findHistorial(@Param("idQuincena")   Long idQuincena,
                                            @Param("idTrabajador") Long idTrabajador);

    long countByQuincena_IdQuincena(Long idQuincena);
}
