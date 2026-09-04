package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.Trabajador;
import com.idat.asistencia.model.enums.EstadoTrabajador;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrabajadorRepository extends JpaRepository<Trabajador, Long> {

    Page<Trabajador> findAllByEstado(EstadoTrabajador estado, Pageable pageable);

    boolean existsByNroDocumento(String nroDocumento);

    boolean existsByEmail(String email);

    Optional<Trabajador> findByNroDocumento(String nroDocumento);

    // ---------- Marcacion (CU03) ----------

    /**
     * Localiza al trabajador por su codigo de barras.
     *
     * Reemplaza el parseo que hacia marcar() en el prototipo, que extraia
     * el identificador del propio codigo ("10023IN" -> id 10023 mas sufijo
     * IN u OU). Aquel esquema implicaba dos codigos por trabajador, lo que
     * RT-02 prohibe, y acoplaba el algoritmo de marcacion a la forma del
     * codigo.
     */
    @EntityGraph(attributePaths = {"puesto", "puesto.area"})
    Optional<Trabajador> findByCodigoBarras(String codigoBarras);

    boolean existsByCodigoBarras(String codigoBarras);

    // ---------- Grupos (RN-20, RN-21) ----------

    List<Trabajador> findByGrupoTrabajo_IdGrupo(Integer idGrupo);

    long countByGrupoTrabajo_IdGrupo(Integer idGrupo);

    /**
     * Trabajadores activos de un area que aun no pertenecen a ningun
     * grupo. Es la lista de candidatos al armar un grupo: filtra por area
     * (RN-20) y excluye a quienes ya estan en otro (RN-21).
     */
    @Query("""
        SELECT t FROM Trabajador t
        JOIN FETCH t.puesto p
        JOIN FETCH p.area a
        WHERE a.idArea = :idArea
          AND t.estado = com.idat.asistencia.model.enums.EstadoTrabajador.ACTIVO
          AND t.grupoTrabajo IS NULL
        ORDER BY t.aPaterno ASC, t.pNombre ASC
    """)
    List<Trabajador> findDisponiblesParaGrupo(@Param("idArea") Integer idArea);

    /** Bloquea la desactivacion de un area o puesto en uso (RN-15). */
    long countByPuesto_Area_IdAreaAndEstado(Integer idArea, EstadoTrabajador estado);

    long countByPuesto_IdPuestoAndEstado(Integer idPuesto, EstadoTrabajador estado);

    // ---------- Consultas existentes ----------

    @EntityGraph(attributePaths = {"puesto", "puesto.area", "genero"})
    Page<Trabajador> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"puesto", "puesto.area", "genero"})
    Optional<Trabajador> findById(Long idTrabajador);

    @EntityGraph(attributePaths = {"puesto", "puesto.area", "genero"})
    @Query("""
        SELECT t FROM Trabajador t
        WHERE t.estado = :estado AND (
            LOWER(t.nroDocumento)     LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(t.pNombre)          LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(t.aPaterno)         LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(t.aMaterno)         LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(t.puesto.puesto)    LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(t.puesto.area.area) LIKE LOWER(CONCAT('%', :q, '%'))
        )
    """)
    Page<Trabajador> buscarPorTermino(@Param("q") String q,
                                      @Param("estado") EstadoTrabajador estado,
                                      Pageable pageable);
}
