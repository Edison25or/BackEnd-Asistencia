package com.idat.asistencia.service;

import com.idat.asistencia.dto.TrabajadorRequestDTO;
import com.idat.asistencia.dto.TrabajadorResponseDTO;
import com.idat.asistencia.model.enums.EstadoTrabajador;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TrabajadorService {
    TrabajadorResponseDTO crearTrabajador(TrabajadorRequestDTO requestDTO);

    // rolEditor: rol del usuario autenticado que ejecuta la actualización
    TrabajadorResponseDTO actualizarTrabajador(Long id, TrabajadorRequestDTO requestDTO, String rolEditor);

    Page<TrabajadorResponseDTO> obtenerTodosLosTrabajadores(EstadoTrabajador estado, Pageable pageable);
    TrabajadorResponseDTO obtenerTrabajadorById(Long id);
    void cesarTrabajador(Long id, String motivo);
    TrabajadorResponseDTO reingresarTrabajador(Long id, Integer idPuesto);
    Page<TrabajadorResponseDTO> buscarTrabajadores(String q, EstadoTrabajador estado, Pageable pageable);
    void resetearPassword(Long id);
}