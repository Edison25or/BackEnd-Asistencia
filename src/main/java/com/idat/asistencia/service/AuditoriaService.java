package com.idat.asistencia.service;

import com.idat.asistencia.model.entity.Auditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface AuditoriaService {

    void registrar(String tabla, Long idRegistro, String accion);

    void registrarCampo(String tabla, Long idRegistro, String accion,
                        String campo, String valorAnterior, String valorNuevo);

    void registrarCambios(String tabla, Long idRegistro, Map<String, String[]> cambios);

    /**
     * Accion con justificacion obligatoria: cese, reapertura, correccion
     * de marcacion (RN-02).
     *
     * El prototipo concatenaba el motivo dentro de valorNuevo, lo que
     * impedia filtrar por el y mezclaba dos cosas distintas.
     */
    void registrarConMotivo(String tabla, Long idRegistro, String accion, String motivo);

    List<Auditoria> getHistorial(String tabla, Long idRegistro);

    Page<Auditoria> buscar(String tabla, String accion, Integer idUsuario,
                           LocalDateTime desde, LocalDateTime hasta,
                           Pageable pageable);
}
