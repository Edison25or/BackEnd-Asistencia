package com.idat.asistencia.service;

import com.idat.asistencia.model.entity.ParametrosQuincena;
import com.idat.asistencia.model.entity.ProgramacionSemanal;
import com.idat.asistencia.model.entity.Quincena;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Generacion de pre-registros y resolucion de quincena (CU14).
 * Unica implementacion: reemplaza las dos copias que el prototipo tenia
 * en AsistenciaServiceImpl y ProgramacionSemanalServiceImpl.
 */
public interface PreRegistroService {

    ResultadoGeneracion generar(List<ProgramacionSemanal> programaciones,
                                LocalDate inicioSemana, LocalDate finSemana);

    /** Quincena que contiene el instante. La crea si no existe. */
    Quincena resolverOCrear(LocalDateTime instante, ParametrosQuincena pq);

    Quincena resolverOCrear(LocalDateTime instante);

    /**
     * @param creados   pre-registros nuevos
     * @param omitidos  duplicados o de quincena cerrada
     * @param enFeriado cuantos caen en dia feriado
     * @param quincenas quincenas involucradas; pueden ser dos si la
     *                  semana cruza el corte del 15 o de fin de mes
     */
    record ResultadoGeneracion(int creados, int omitidos, int enFeriado,
                               List<Quincena> quincenas) {}
}
