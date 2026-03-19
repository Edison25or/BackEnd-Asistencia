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

    // ── Para crear un nuevo esquema (v1): verificar nombre base único ──
    boolean existsByGrupoNombreIgnoreCase(String grupoNombre);

    // ── Versión activa de un grupo ─────────────────────────────
    Optional<EsquemaHorario> findByGrupoNombreAndVigenteHastaIsNull(String grupoNombre);

    // ── Todas las versiones de un grupo, ordenadas desc ────────
    List<EsquemaHorario> findByGrupoNombreOrderByVersionDesc(String grupoNombre);

    // ── Solo versiones activas (para dropdowns de asignación) ──
    @Query("SELECT e FROM EsquemaHorario e WHERE e.activo = true AND e.vigenteHasta IS NULL ORDER BY e.nombre ASC")
    List<EsquemaHorario> findAllVigentesActivos();

    // ── Todos los grupos (versión activa de cada uno), para la lista principal ──
    @Query("""
        SELECT e FROM EsquemaHorario e
        WHERE e.vigenteHasta IS NULL
        ORDER BY e.grupoNombre ASC, e.version DESC
    """)
    List<EsquemaHorario> findAllVersionesActivas();

    // ── Número de programaciones que usan este esquema ─────────
    @Query("SELECT COUNT(p) FROM ProgramacionSemanal p WHERE p.esquema.idEsquema = :idEsquema")
    long countProgramacionesByEsquema(@Param("idEsquema") Integer idEsquema);

    // ── Versión máxima de un grupo ─────────────────────────────
    @Query("SELECT MAX(e.version) FROM EsquemaHorario e WHERE e.grupoNombre = :grupoNombre")
    Integer findMaxVersionByGrupoNombre(@Param("grupoNombre") String grupoNombre);
}