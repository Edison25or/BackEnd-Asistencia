package com.idat.asistencia.service;

import com.idat.asistencia.model.entity.Auditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface AuditoriaService {

    /**
     * Registra una acción simple sin cambio de campo (CREAR, CESAR, REINGRESAR, RESET_PASSWORD).
     */
    void registrar(String tabla, Long idRegistro, String accion);

    /**
     * Registra un cambio de campo único (un solo valor antes → después).
     */
    void registrarCampo(String tabla, Long idRegistro, String accion,
                        String campo, String valorAnterior, String valorNuevo);

    /**
     * Registra múltiples cambios de campos en una sola operación de modificación.
     * El mapa tiene como clave el nombre del campo y como valor un array de
     * dos elementos: [valorAnterior, valorNuevo].
     * Solo persiste los campos donde valorAnterior != valorNuevo.
     */
    void registrarCambios(String tabla, Long idRegistro, Map<String, String[]> cambios);

    /** Historial completo de un registro */
    List<Auditoria> getHistorial(String tabla, Long idRegistro);

    /** Búsqueda paginada con filtros */
    Page<Auditoria> buscar(String tabla, String accion, Long idUsuario,
                           LocalDateTime desde, LocalDateTime hasta,
                           Pageable pageable);
}