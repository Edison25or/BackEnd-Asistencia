package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.EsquemaHorario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EsquemaHorarioRepository extends JpaRepository<EsquemaHorario, Integer> {

    boolean existsByGrupoNombreIgnoreCase(String grupoNombre);

    Optional<EsquemaHorario> findByGrupoNombreAndVigenteHastaIsNull(String grupoNombre);

    List<EsquemaHorario> findByGrupoNombreOrderByVersionDesc(String grupoNombre);

    @Query("""
        SELECT e FROM EsquemaHorario e
        JOIN FETCH e.turno
        WHERE e.activo = true AND e.vigenteHasta IS NULL
        ORDER BY e.nombre ASC
    """)
    List<EsquemaHorario> findAllVigentesActivos();

    @Query("""
        SELECT e FROM EsquemaHorario e
        JOIN FETCH e.turno
        WHERE e.vigenteHasta IS NULL
        ORDER BY e.grupoNombre ASC, e.version DESC
    """)
    List<EsquemaHorario> findAllVersionesActivas();

    @Query("SELECT COUNT(p) FROM ProgramacionSemanal p WHERE p.esquema.idEsquema = :idEsquema")
    long countProgramacionesByEsquema(@Param("idEsquema") Integer idEsquema);

    @Query("SELECT MAX(e.version) FROM EsquemaHorario e WHERE e.grupoNombre = :grupoNombre")
    Integer findMaxVersionByGrupoNombre(@Param("grupoNombre") String grupoNombre);

    /** Esquemas vigentes que usan un turno. Bloquea su desactivacion. */
    long countByTurno_IdTurnoAndVigenteHastaIsNull(Integer idTurno);
}
