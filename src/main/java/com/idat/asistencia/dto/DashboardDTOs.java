package com.idat.asistencia.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Estadisticas para la toma de decisiones (dashboard).
 *
 * ============================================================
 * POR QUE ESTO NO SALE DEL REPORTE EXISTENTE
 * ============================================================
 * El reporte de asistencias devuelve una fila por jornada, pensado para
 * consultar un caso concreto. Agregarlo en el navegador sobre un periodo
 * de un mes con ochenta trabajadores significa transportar unas 2400
 * filas para mostrar seis numeros.
 *
 * ============================================================
 * QUE SE MIDE Y POR QUE
 * ============================================================
 * Las cifras sueltas dicen que paso; lo que permite decidir es verlas
 * repartidas. Por eso ademas de los totales se entregan tres cortes:
 *
 *   - Tendencia diaria: distingue un mal dia de un problema sostenido.
 *   - Por area y por turno: es donde aparecen los desequilibrios.
 *   - Sobrecarga por trabajador: quien acumula horas por encima de lo
 *     esperado, que es lo que permite actuar antes de que se convierta
 *     en un problema.
 */
public class DashboardDTOs {

    @Data
    @Builder
    public static class EstadisticasResponse {

        // ---------- Periodo consultado ----------
        private String desde;
        private String hasta;
        private String areaNombre;      // null = todas
        private Integer diasPeriodo;

        // ---------- Indicadores principales ----------
        /** Jornadas con marcacion de entrada, sobre el total programado. */
        private int totalJornadas;
        private int jornadasTrabajadas;

        /**
         * Porcentaje de jornadas trabajadas sin tardanza.
         *
         * Se calcula sobre las TRABAJADAS y no sobre el total programado:
         * una falta no es una impuntualidad, y mezclarlas haria que un dia
         * con muchas ausencias justificadas pareciera un problema de
         * puntualidad.
         */
        private double tasaPuntualidad;

        private int totalTardanzas;
        private int minutosTardanza;

        private int faltasInjustificadas;
        private int diasPermiso;
        private int diasFaltaJustificada;

        /**
         * Trabajadores sin ninguna tardanza ni falta en el periodo, entre
         * los que tuvieron al menos una jornada programada.
         */
        private int asistenciasPerfectas;
        private int trabajadoresConJornadas;

        // ---------- Horas ----------
        private int minutosTrabajados;
        private int minutosEsperados;
        private int minutosExtraReconocidos;
        private int minutosExtraPendientes;
        private int minutosFeriado;
        private String horasTrabajadas;
        private String horasExtra;
        private String horasFeriado;

        /** Registros que bloquean el cierre de quincena (RN-37). */
        private int pendientesRevision;
        private int marcacionesIncompletas;

        // ---------- Cortes ----------
        private List<PuntoDiario>   tendenciaDiaria;
        private List<CorteArea>     porArea;
        private List<CorteTurno>    porTurno;
        private List<FilaTrabajador> sobrecarga;
        private List<FilaTrabajador> rankingTardanzas;
        private List<FilaTrabajador> rankingFaltas;
    }

    /** Un dia de la tendencia. */
    @Data
    @Builder
    public static class PuntoDiario {
        private String fecha;
        private String diaSemana;
        private int    programadas;
        private int    trabajadas;
        private int    tardanzas;
        private int    faltas;
        private int    minutosTrabajados;
        private boolean esFeriado;
    }

    @Data
    @Builder
    public static class CorteArea {
        private Integer idArea;
        private String  area;
        private int     trabajadores;
        private int     jornadas;
        private int     tardanzas;
        private int     faltas;
        private int     minutosTrabajados;
        private int     minutosExtra;
        private double  tasaPuntualidad;
        private String  horasTrabajadas;
    }

    @Data
    @Builder
    public static class CorteTurno {
        private String turno;
        private int    jornadas;
        private int    tardanzas;
        private int    minutosNormales;
        private int    minutosExtra;
        private int    minutosFeriado;
        private double tasaPuntualidad;
        private String horasNormales;
        private String horasExtra;
    }

    /**
     * Fila de trabajador, reutilizada por los tres rankings.
     *
     * saldoMinutos es trabajado menos esperado: positivo indica carga por
     * encima de lo programado, que es la lectura util para detectar
     * sobrecarga antes de que se convierta en un problema.
     */
    @Data
    @Builder
    public static class FilaTrabajador {
        private Long    idTrabajador;
        private String  nombre;
        private String  area;
        private String  puesto;
        private int     jornadas;
        private int     minutosTrabajados;
        private int     minutosEsperados;
        private int     saldoMinutos;
        private String  saldoHoras;
        private int     tardanzas;
        private int     minutosTardanza;
        private int     faltas;
        private String  horasTrabajadas;
    }
}
