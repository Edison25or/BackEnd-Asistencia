package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.PeriodoLaboral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PeriodoLaboralRepository extends JpaRepository<PeriodoLaboral, Long> {

    // Busca el contrato que no tiene fecha de cese (el contrato actual)
    @Query("SELECT p FROM PeriodoLaboral p WHERE p.trabajador.idTrabajador = :idTrabajador AND p.fechaCese IS NULL")
    Optional<PeriodoLaboral> findPeriodoActivo(@Param("idTrabajador") Long idTrabajador);
}