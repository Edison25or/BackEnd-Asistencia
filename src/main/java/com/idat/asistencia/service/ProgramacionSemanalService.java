package com.idat.asistencia.service;

import com.idat.asistencia.dto.ProgramacionDTOs.*;
import java.util.List;

public interface ProgramacionSemanalService {
    List<ProgramacionResponse> getAll();
    List<ProgramacionResponse> getBySemana(String semanaInicio);
    ProgramacionResponse crear(ProgramacionRequest request);
    ProgramacionBulkResponse crearDesdeGrupo(Integer idGrupo, String semanaInicio, Integer idEsquema);
    void eliminar(Long id);

    /**
     * Confirma la programación de una semana:
     * 1. Valida que la semana no esté en el pasado.
     * 2. Determina a qué quincena pertenece.
     * 3. Genera pre-registros de asistencia por trabajador × día laborable.
     * 4. Devuelve un resumen con totales y la quincena asignada.
     */
    ConfirmarSemanaResponse confirmarSemana(String semanaInicio);
}