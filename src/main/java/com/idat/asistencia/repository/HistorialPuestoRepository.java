package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.HistorialPuesto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HistorialPuestoRepository extends JpaRepository<HistorialPuesto, Long> {

    // Busca el puesto que no tiene fecha de fin (el puesto actual)
    @Query("SELECT h FROM HistorialPuesto h WHERE h.trabajador.idTrabajador = :idTrabajador AND h.fechaFin IS NULL")
    Optional<HistorialPuesto> findPuestoActivo(@Param("idTrabajador") Long idTrabajador);
}