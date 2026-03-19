package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.ProgramacionSemanal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProgramacionSemanalRepository extends JpaRepository<ProgramacionSemanal, Long> {

    // Para el servicio de asistencia — busca por trabajador en fecha
    @Query("""
        SELECT p FROM ProgramacionSemanal p
        JOIN FETCH p.esquema
        WHERE :fecha BETWEEN p.semanaInicio AND p.semanaFin
          AND p.trabajador.idTrabajador = :idTrabajador
    """)
    Optional<ProgramacionSemanal> findEsquemaParaTrabajadorEnFecha(
            @Param("idTrabajador") Long idTrabajador,
            @Param("fecha") LocalDate fecha
    );

    // Verifica si un trabajador ya tiene programación en una semana (para evitar duplicados en bulk)
    @Query("""
        SELECT COUNT(p) > 0 FROM ProgramacionSemanal p
        WHERE p.trabajador.idTrabajador = :idTrabajador
          AND p.semanaInicio = :semanaInicio
    """)
    boolean existsByTrabajadorAndSemana(
            @Param("idTrabajador") Long idTrabajador,
            @Param("semanaInicio") LocalDate semanaInicio
    );

    // Panel de programación — todos
    @Query("""
        SELECT p FROM ProgramacionSemanal p
        JOIN FETCH p.esquema
        JOIN FETCH p.trabajador t
        JOIN FETCH t.puesto pu
        JOIN FETCH pu.area
    """)
    List<ProgramacionSemanal> findAllWithRelations();

    // Filtrar por semana
    @Query("""
        SELECT p FROM ProgramacionSemanal p
        JOIN FETCH p.esquema
        JOIN FETCH p.trabajador t
        JOIN FETCH t.puesto pu
        JOIN FETCH pu.area
        WHERE p.semanaInicio = :semanaInicio
    """)
    List<ProgramacionSemanal> findBySemanaInicio(@Param("semanaInicio") LocalDate semanaInicio);
}