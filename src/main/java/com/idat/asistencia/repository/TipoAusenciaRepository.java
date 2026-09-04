package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.TipoAusencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TipoAusenciaRepository extends JpaRepository<TipoAusencia, Integer> {

    List<TipoAusencia> findByActivoTrueOrderByNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);
}
