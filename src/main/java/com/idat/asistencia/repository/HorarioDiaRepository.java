package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.HorarioDia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HorarioDiaRepository extends JpaRepository<HorarioDia, Long> {

    // Busca el horario del día de la semana dentro de un esquema dado
    @Query("""
        SELECT h FROM HorarioDia h
        WHERE h.esquema.idEsquema = :idEsquema
        AND h.diaSemana = :diaSemana
        AND h.esDescanso = false
        """)
    Optional<HorarioDia> findByEsquemaAndDia(
            @Param("idEsquema") Integer idEsquema,
            @Param("diaSemana") int diaSemana
    );
}
