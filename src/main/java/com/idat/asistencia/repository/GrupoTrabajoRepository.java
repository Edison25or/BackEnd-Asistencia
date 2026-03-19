package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.GrupoTrabajo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrupoTrabajoRepository extends JpaRepository<GrupoTrabajo, Integer> {

    boolean existsByNombre(String nombre);

    @Query("SELECT DISTINCT g FROM GrupoTrabajo g LEFT JOIN FETCH g.trabajadores t LEFT JOIN FETCH t.puesto p LEFT JOIN FETCH p.area")
    List<GrupoTrabajo> findAllWithTrabajadores();

    // Para crearDesdeGrupo — necesita los trabajadores cargados (evita lazy-load vacío)
    @Query("SELECT g FROM GrupoTrabajo g LEFT JOIN FETCH g.trabajadores t LEFT JOIN FETCH t.puesto p LEFT JOIN FETCH p.area WHERE g.idGrupo = :id")
    Optional<GrupoTrabajo> findByIdWithTrabajadores(@Param("id") Integer id);

    // Busca el grupo al que pertenece un trabajador (si existe)
    @Query("SELECT g FROM GrupoTrabajo g JOIN g.trabajadores t WHERE t.idTrabajador = :idTrabajador")
    Optional<GrupoTrabajo> findByTrabajadorId(@Param("idTrabajador") Long idTrabajador);

    // Busca el grupo al que pertenece un trabajador, excluyendo un grupo específico (para edición)
    @Query("SELECT g FROM GrupoTrabajo g JOIN g.trabajadores t WHERE t.idTrabajador = :idTrabajador AND g.idGrupo <> :idGrupoExcluir")
    Optional<GrupoTrabajo> findByTrabajadorIdExcludingGrupo(@Param("idTrabajador") Long idTrabajador, @Param("idGrupoExcluir") Integer idGrupoExcluir);
}