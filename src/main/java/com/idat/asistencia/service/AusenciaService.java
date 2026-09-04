package com.idat.asistencia.service;

import com.idat.asistencia.dto.AusenciaDTOs.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Permisos y faltas justificadas (CU16, CU17), y la neutralizacion de
 * pre-registros que ambos producen (RN-44).
 */
public interface AusenciaService {

    PermisoResponse registrarPermiso(PermisoRequest req);

    FaltaJustificadaResponse registrarFaltaJustificada(FaltaJustificadaRequest req);

    List<PermisoResponse> listarPermisos(Long idTrabajador, LocalDate desde, LocalDate hasta);

    List<FaltaJustificadaResponse> listarFaltasJustificadas(
            Long idTrabajador, LocalDate desde, LocalDate hasta);

    /**
     * Elimina un permiso y REVIERTE su efecto sobre los pre-registros
     * (RN-44).
     *
     * Eliminar sin revertir dejaria los pre-registros en estado REVISADO
     * con una referencia rota: nadie los reportaria como falta y nadie
     * sabria por que. Peor aun, la FK impediria el borrado y el error
     * llegaria al usuario como un fallo de base de datos.
     *
     * Solo se permite si la quincena sigue abierta: un consolidado ya
     * emitido no se altera (RN-32).
     *
     * @return numero de pre-registros que volvieron a estado pendiente
     */
    int eliminarPermiso(Long idPermiso);

    /** Igual que eliminarPermiso, para faltas justificadas. */
    int eliminarFaltaJustificada(Long idFalta);

    /**
     * Aplica sobre los pre-registros ya generados las ausencias que un
     * trabajador tenga registradas en un rango.
     *
     * Se invoca tambien desde la generacion de pre-registros (CU14),
     * porque la ausencia puede haberse registrado ANTES de que se
     * programe la semana.
     *
     * @return numero de pre-registros neutralizados
     */
    int neutralizarPorAusenciasExistentes(Long idTrabajador, LocalDate desde, LocalDate hasta);
}
