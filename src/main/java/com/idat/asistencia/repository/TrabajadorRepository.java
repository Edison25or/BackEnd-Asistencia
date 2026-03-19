package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.Trabajador;
import com.idat.asistencia.model.enums.EstadoTrabajador;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrabajadorRepository extends JpaRepository<Trabajador, Long> {

    Page<Trabajador> findAllByEstado(EstadoTrabajador estado, Pageable pageable);

    boolean existsByNroDocumento(String nroDocumento);

    boolean existsByEmail(String email);

    @EntityGraph(attributePaths = {"puesto", "puesto.area", "genero"})
    Page<Trabajador> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"puesto", "puesto.area", "genero"})
    Optional<Trabajador> findById(Long idTrabajador);

    @EntityGraph(attributePaths = {"puesto", "puesto.area", "genero"})
    @Query("""
    SELECT t FROM Trabajador t
    WHERE t.estado = :estado AND (
        LOWER(t.nroDocumento) LIKE LOWER(CONCAT('%', :q, '%')) OR
        LOWER(t.pNombre)      LIKE LOWER(CONCAT('%', :q, '%')) OR
        LOWER(t.aPaterno)     LIKE LOWER(CONCAT('%', :q, '%')) OR
        LOWER(t.aMaterno)     LIKE LOWER(CONCAT('%', :q, '%')) OR
        LOWER(t.puesto.puesto)      LIKE LOWER(CONCAT('%', :q, '%')) OR
        LOWER(t.puesto.area.area)   LIKE LOWER(CONCAT('%', :q, '%'))
    )
    """)
    Page<Trabajador> buscarPorTermino(
            @Param("q") String q,
            @Param("estado") EstadoTrabajador estado,
            Pageable pageable
    );

}