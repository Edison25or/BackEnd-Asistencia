package com.idat.asistencia.model.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Configuracion global de asistencia. Registro unico: siempre id = 1
 * (RN-28, CU27). Editable solo por el Superadministrador, sin requerir
 * despliegue de codigo (RNF014).
 */
@Entity
@Table(name = "parametros_generales_asistencia")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ParametrosGeneralesAsistencia {

    /** Identificador fijo del registro unico. */
    public static final Integer ID_UNICO = 1;

    @Id
    @Column(name = "id_parametros")
    @Builder.Default
    private Integer idParametros = ID_UNICO;

    /**
     * P1: maxima anticipacion de entrada, en minutos.
     * Se valida al registrar la ENTRADA.
     */
    @Column(name = "max_anticipacion_entrada", nullable = false)
    @Builder.Default
    private Integer maxAnticipacionEntrada = 120;

    /**
     * P2: maximo exceso de salida, en minutos.
     * Se valida al registrar la SALIDA.
     */
    @Column(name = "max_exceso_salida", nullable = false)
    @Builder.Default
    private Integer maxExcesoSalida = 120;

    /**
     * P3: tope combinado, en minutos.
     * Solo puede evaluarse en la SALIDA, cuando ya se conocen ambos
     * extremos de la jornada. El prototipo lo evaluaba en la entrada,
     * donde la hora de salida todavia no existe.
     */
    @Column(name = "tope_combinado", nullable = false)
    @Builder.Default
    private Integer topeCombinado = 180;

    /**
     * Segundos que el lector espera el segundo escaneo que confirma una
     * entrada anticipada (HU-22). Valor sugerido: 20 a 30.
     */
    @Column(name = "ventana_confirmacion_seg", nullable = false)
    @Builder.Default
    private Integer ventanaConfirmacionSeg = 25;

    /**
     * Segundos durante los cuales se ignora un nuevo escaneo del mismo
     * trabajador (HU-53). Debe ser MENOR que ventanaConfirmacionSeg: si no,
     * el segundo escaneo de una entrada anticipada se descartaria como
     * rebote y la confirmacion nunca podria completarse.
     */
    @Column(name = "intervalo_antirrebote_seg", nullable = false)
    @Builder.Default
    private Integer intervaloAntirreboteSeg = 10;

    /**
     * Si es true, los minutos de refrigerio que caen dentro de un feriado
     * se descuentan en proporcion del computo de minutosFeriado.
     * Decision pendiente PD-03 (corresponde a Contabilidad).
     */
    @Column(name = "descontar_refrigerio_feriado", nullable = false)
    @Builder.Default
    private boolean descontarRefrigerioFeriado = true;

    /**
     * Valida la coherencia entre P1, P2, P3 y los tiempos del lector.
     * Debe invocarse desde el servicio antes de persistir (CU27).
     *
     * @return null si la configuracion es valida, o el mensaje de error.
     */
    @Transient
    public String validar() {
        if (maxAnticipacionEntrada == null || maxAnticipacionEntrada < 0)
            return "La maxima anticipacion de entrada (P1) debe ser cero o positiva.";
        if (maxExcesoSalida == null || maxExcesoSalida < 0)
            return "El maximo exceso de salida (P2) debe ser cero o positivo.";
        if (topeCombinado == null)
            return "El tope combinado (P3) es obligatorio.";

        int mayor = Math.max(maxAnticipacionEntrada, maxExcesoSalida);
        if (topeCombinado < mayor)
            return "El tope combinado (P3 = " + topeCombinado + ") no puede ser menor "
                 + "que el mayor entre P1 y P2 (" + mayor + ").";

        int suma = maxAnticipacionEntrada + maxExcesoSalida;
        if (topeCombinado > suma)
            return "El tope combinado (P3 = " + topeCombinado + ") no puede exceder "
                 + "la suma de P1 y P2 (" + suma + ").";

        if (ventanaConfirmacionSeg == null || ventanaConfirmacionSeg <= 0)
            return "La ventana de confirmacion debe ser mayor que cero.";
        if (intervaloAntirreboteSeg == null || intervaloAntirreboteSeg < 0)
            return "El intervalo anti-rebote debe ser cero o positivo.";
        if (intervaloAntirreboteSeg >= ventanaConfirmacionSeg)
            return "El intervalo anti-rebote (" + intervaloAntirreboteSeg + "s) debe ser "
                 + "menor que la ventana de confirmacion (" + ventanaConfirmacionSeg + "s), "
                 + "o el segundo escaneo de una entrada anticipada se descartaria como rebote.";

        return null;
    }
}
