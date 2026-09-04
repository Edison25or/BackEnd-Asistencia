package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.ConsolidadoTurno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConsolidadoTurnoRepository extends JpaRepository<ConsolidadoTurno, Long> {

    List<ConsolidadoTurno> findByConsolidado_Id(Long idConsolidado);

    void deleteByConsolidado_Id(Long idConsolidado);
}
