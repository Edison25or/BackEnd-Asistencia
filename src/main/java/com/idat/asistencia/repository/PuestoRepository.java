package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.Puesto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PuestoRepository extends JpaRepository<Puesto, Integer> {
    List<Puesto> findByArea_IdArea(Integer idArea);
    List<Puesto> findByArea_IdAreaAndActivoTrue(Integer idArea);
    List<Puesto> findAllByOrderByArea_AreaAscPuestoAsc();
    boolean existsByPuestoIgnoreCaseAndArea_IdArea(String puesto, Integer idArea);
    boolean existsByPuestoIgnoreCaseAndArea_IdAreaAndIdPuestoNot(String puesto, Integer idArea, Integer idPuesto);
}