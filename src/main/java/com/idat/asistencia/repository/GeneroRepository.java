package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.Genero;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface GeneroRepository extends JpaRepository<Genero, Integer> {
    List<Genero> findAllByOrderByGeneroAsc();
    List<Genero> findByActivoTrueOrderByGeneroAsc();
    boolean existsByGeneroIgnoreCase(String genero);
    boolean existsByGeneroIgnoreCaseAndIdGeneroNot(String genero, Integer idGenero);
    Optional<Genero> findByGeneroIgnoreCase(String genero);
}