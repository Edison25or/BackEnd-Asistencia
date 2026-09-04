package com.idat.asistencia.service;

import com.idat.asistencia.dto.FeriadoDTOs.*;
import com.idat.asistencia.model.entity.Asistencia;

import java.time.LocalDate;
import java.util.List;

/** Catalogo de feriados y computo de horas trabajadas en ellos (RN-41). */
public interface FeriadoService {

    /** Minutos de la jornada que caen dentro de dias feriados. */
    int calcularMinutosFeriado(Asistencia asistencia);

    boolean esFeriado(LocalDate fecha);

    List<FeriadoResponse> listar();

    /** Conteo de registros que seran afectados, ANTES de confirmar. */
    ImpactoFeriadoResponse previsualizar(LocalDate fecha);

    ImpactoFeriadoResponse registrar(FeriadoRequest req);

    void desactivar(Integer idFeriado);

    /** Recalcula minutos y marca pre-registros de una fecha feriada. */
    ImpactoFeriadoResponse aplicarSobreRegistros(LocalDate fecha);
}
