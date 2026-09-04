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

    Optional<GrupoTrabajo> findByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCase(String nombre);

    /** Grupos de un area. La restriccion de area unica es RN-20. */
    List<GrupoTrabajo> findByArea_IdArea(Integer idArea);

    /**
     * Grupo con sus miembros. Al haber pasado la relacion a @OneToMany, el
     * fetch se hace sobre la coleccion inversa.
     */
    @Query("""
        SELECT g FROM GrupoTrabajo g
        LEFT JOIN FETCH g.trabajadores t
        LEFT JOIN FETCH t.puesto p
        LEFT JOIN FETCH p.area
        WHERE g.idGrupo = :idGrupo
    """)
    Optional<GrupoTrabajo> findByIdWithTrabajadores(@Param("idGrupo") Integer idGrupo);

    @Query("""
        SELECT DISTINCT g FROM GrupoTrabajo g
        JOIN FETCH g.area
        LEFT JOIN FETCH g.trabajadores
    """)
    List<GrupoTrabajo> findAllWithTrabajadores();
}
