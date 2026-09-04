package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.Quincena;
import com.idat.asistencia.model.enums.EstadoQuincena;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface QuincenaRepository extends JpaRepository<Quincena, Long> {

    Optional<Quincena> findByAnioAndMesAndNumero(Integer anio, Integer mes, Integer numero);

    List<Quincena> findByEstadoOrderByInicioDesc(EstadoQuincena estado);

    List<Quincena> findAllByOrderByInicioDesc();

    /**
     * Quincena que contiene el instante dado, con rango SEMIABIERTO
     * [inicio, fin).
     *
     * Reemplaza a findByFechaAproximada(), que tenia dos defectos: su
     * condicion OR podia devolver varias filas sobre un tipo de retorno
     * Optional, lo que produce NonUniqueResultException en cuanto existe
     * mas de una quincena que coincide; y comparaba solo fechas, de modo
     * que ignoraba la hora de corte del ultimo dia del periodo.
     *
     * Con limites semiabiertos, dos quincenas consecutivas teselan sin
     * solaparse: una jornada que entra exactamente a la hora de corte
     * pertenece a la quincena siguiente, nunca a las dos.
     */
    @Query("""
        SELECT q FROM Quincena q
        WHERE :instante >= q.inicio
          AND :instante <  q.fin
    """)
    Optional<Quincena> findQueContiene(@Param("instante") LocalDateTime instante);

    /** Comprueba solapamiento antes de crear, para no duplicar periodos. */
    @Query("""
        SELECT COUNT(q) > 0 FROM Quincena q
        WHERE q.inicio < :fin
          AND q.fin    > :inicio
    """)
    boolean existeSolapada(@Param("inicio") LocalDateTime inicio,
                           @Param("fin")    LocalDateTime fin);
}
