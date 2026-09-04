package com.idat.asistencia.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Configuracion de los cortes de quincena. Registro unico: siempre id = 1
 * (RN-35, CU26).
 *
 * El analisis original describia estos parametros como texto ("30/31 18:00",
 * "15 18:00"). Aqui se modelan como dia y hora, que es equivalente y permite
 * calcular los rangos sin parsear cadenas.
 *
 * Con diaCorteIntermedio = 15 y horaCorte = 18:00 los rangos quedan asi:
 *   Quincena 1: ultimo dia del mes anterior 18:00  ->  dia 15 a las 18:00
 *   Quincena 2: dia 15 a las 18:00                 ->  ultimo dia del mes 18:00
 *
 * Los rangos son semiabiertos [inicio, fin): una jornada que entra
 * exactamente a las 18:00 del dia 15 pertenece a la segunda quincena.
 */
@Entity
@Table(name = "parametros_quincena")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ParametrosQuincena {

    public static final Integer ID_UNICO = 1;

    @Id
    @Column(name = "id_parametros")
    @Builder.Default
    private Integer idParametros = ID_UNICO;

    /** Dia del mes en que corta la primera quincena. */
    @Column(name = "dia_corte_intermedio", nullable = false)
    @Builder.Default
    private Integer diaCorteIntermedio = 15;

    /** Hora de corte, aplicada a ambos limites. */
    @Column(name = "hora_corte", nullable = false)
    @Builder.Default
    private LocalTime horaCorte = LocalTime.of(18, 0);

    /**
     * Inicio de la quincena indicada, inclusivo.
     *
     * @param numero 1 = primera quincena, 2 = segunda
     */
    @Transient
    public LocalDateTime inicioDe(int anio, int mes, int numero) {
        if (numero == 1) {
            LocalDate finMesAnterior = LocalDate.of(anio, mes, 1)
                    .minusDays(1);
            return finMesAnterior.atTime(horaCorte);
        }
        return LocalDate.of(anio, mes, diaCorteIntermedio).atTime(horaCorte);
    }

    /**
     * Fin de la quincena indicada, exclusivo.
     *
     * @param numero 1 = primera quincena, 2 = segunda
     */
    @Transient
    public LocalDateTime finDe(int anio, int mes, int numero) {
        if (numero == 1) {
            return LocalDate.of(anio, mes, diaCorteIntermedio).atTime(horaCorte);
        }
        LocalDate primero  = LocalDate.of(anio, mes, 1);
        LocalDate ultimo   = primero.withDayOfMonth(primero.lengthOfMonth());
        return ultimo.atTime(horaCorte);
    }

    /** @return null si la configuracion es valida, o el mensaje de error. */
    @Transient
    public String validar() {
        if (diaCorteIntermedio == null || diaCorteIntermedio < 1 || diaCorteIntermedio > 28)
            return "El dia de corte intermedio debe estar entre 1 y 28, "
                 + "para que exista en todos los meses.";
        if (horaCorte == null)
            return "La hora de corte es obligatoria.";
        return null;
    }
}
