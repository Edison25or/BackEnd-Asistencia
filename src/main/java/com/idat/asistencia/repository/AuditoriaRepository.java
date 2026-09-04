package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.Auditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {

    List<Auditoria> findByTablaAndIdRegistroOrderByFechaDesc(String tabla, Long idRegistro);

    /**
     * El filtro por usuario pasa de un idUsuario suelto a la relacion
     * real. En el prototipo ese campo nunca se asignaba, de modo que el
     * filtro no devolvia nada nunca.
     */
    @Query("""
        SELECT a FROM Auditoria a
        LEFT JOIN FETCH a.usuario u
        WHERE (:tabla     IS NULL OR a.tabla   = :tabla)
          AND (:accion    IS NULL OR a.accion  = :accion)
          AND (:idUsuario IS NULL OR u.idUsuario = :idUsuario)
          AND (:desde     IS NULL OR a.fecha  >= :desde)
          AND (:hasta     IS NULL OR a.fecha  <= :hasta)
        ORDER BY a.fecha DESC
    """)
    Page<Auditoria> buscar(@Param("tabla")     String  tabla,
                           @Param("accion")    String  accion,
                           @Param("idUsuario") Integer idUsuario,
                           @Param("desde")     LocalDateTime desde,
                           @Param("hasta")     LocalDateTime hasta,
                           Pageable pageable);
}
