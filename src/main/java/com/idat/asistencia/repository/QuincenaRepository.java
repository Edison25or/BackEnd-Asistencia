package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.Quincena;
import com.idat.asistencia.model.enums.EstadoQuincena;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface QuincenaRepository extends JpaRepository<Quincena, Long> {

    Optional<Quincena> findByAnioAndMesAndNumero(Integer anio, Integer mes, Integer numero);

    List<Quincena> findByAnioAndMesOrderByNumeroAsc(Integer anio, Integer mes);

    List<Quincena> findByEstadoOrderByAnioDescMesDescNumeroDesc(EstadoQuincena estado);

    /** Quincena abierta más reciente */
    @Query("""
        SELECT q FROM Quincena q
        WHERE q.estado = 'ABIERTA'
        ORDER BY q.anio DESC, q.mes DESC, q.numero DESC
    """)
    List<Quincena> findAbiertas();

    /**
     * Determina a qué quincena pertenece una fecha/hora dada,
     * usando la lógica de corte a las horaCorte del día anterior.
     * Una asistencia cuyo ingreso_real ocurre entre:
     *   (fechaInicio - 1 día) a las horaCorte
     *   y fechaFin a las horaCorte
     * pertenece a esta quincena.
     */
    @Query("""
        SELECT q FROM Quincena q
        WHERE :fecha BETWEEN q.fechaInicio AND q.fechaFin
           OR (:fecha = q.fechaInicio AND :hora >= q.horaCorte)
        ORDER BY q.anio DESC, q.mes DESC, q.numero DESC
    """)
    Optional<Quincena> findByFechaAproximada(
            @Param("fecha") LocalDate fecha,
            @Param("hora")  java.time.LocalTime hora
    );
}