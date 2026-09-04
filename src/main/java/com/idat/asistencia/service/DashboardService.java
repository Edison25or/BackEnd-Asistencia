package com.idat.asistencia.service;

import com.idat.asistencia.dto.DashboardDTOs.EstadisticasResponse;

import java.time.LocalDate;

/**
 * Estadisticas para la toma de decisiones.
 *
 * Un solo metodo devuelve el conjunto completo: indicadores, tendencia
 * diaria y cortes por area, turno y trabajador. Partirlo en seis
 * endpoints obligaria al frontend a seis peticiones para pintar una
 * pantalla, y cada una recorreria las mismas jornadas.
 */
public interface DashboardService {

    /**
     * @param desde  inicio del periodo, inclusive
     * @param hasta  fin del periodo, inclusive
     * @param idArea filtro opcional. El Jefe queda restringido a su area
     *               aunque pida otra (RN-01).
     */
    EstadisticasResponse calcular(LocalDate desde, LocalDate hasta, Integer idArea);
}
