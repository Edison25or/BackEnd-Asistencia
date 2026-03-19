package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.Area;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AreaRepository extends JpaRepository<Area, Integer> {
    List<Area> findAllByOrderByAreaAsc();
    List<Area> findByActivoTrueOrderByAreaAsc();
    boolean existsByAreaIgnoreCase(String area);
    boolean existsByAreaIgnoreCaseAndIdAreaNot(String area, Integer idArea);
}