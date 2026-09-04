package com.idat.asistencia.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maquina de estados del punto de marcacion (HU-22, HU-53).
 *
 * ============================================================
 * POR QUE EL LECTOR NECESITA MEMORIA
 * ============================================================
 * El endpoint de marcacion del prototipo era sin estado: cada escaneo se
 * resolvia de forma aislada. Eso hace imposibles dos cosas que el
 * analisis exige.
 *
 * 1. La confirmacion por doble escaneo de una entrada anticipada (HU-22).
 *    El sistema debe recordar que le pidio al trabajador volver a pasar
 *    su carne, y durante cuanto tiempo.
 *
 * 2. La guarda anti-rebote (HU-53). Sin ella, un doble escaneo accidental
 *    registra la salida pocos segundos despues de la entrada y cierra la
 *    jornada con horas practicamente nulas.
 *
 * Las dos interactuan: el intervalo anti-rebote debe ser MENOR que la
 * ventana de confirmacion, o el segundo escaneo se descartaria como
 * rebote y la confirmacion nunca podria completarse. La validacion
 * cruzada esta en ParametrosGeneralesAsistencia.validar().
 *
 * ============================================================
 * POR QUE EN MEMORIA
 * ============================================================
 * El estado vive segundos y solo tiene sentido en el punto fisico de
 * marcacion, que es unico (RT-01). Persistirlo agregaria escrituras en el
 * camino critico de RNF006, que exige respuesta en menos de dos segundos
 * durante el pico de cambio de turno.
 *
 * Si se reinicia la aplicacion en medio de una confirmacion pendiente, el
 * trabajador simplemente vuelve a escanear. No se pierde ningun dato
 * registrado, porque una confirmacion pendiente todavia no guardo nada.
 */
@Slf4j
@Component
public class LectorEstadoService {

    /** Confirmaciones pendientes, por trabajador. */
    private final Map<Long, Pendiente> pendientes = new ConcurrentHashMap<>();

    /** Ultimo escaneo aceptado, por trabajador. Para el anti-rebote. */
    private final Map<Long, LocalDateTime> ultimoEscaneo = new ConcurrentHashMap<>();

    /**
     * Confirmacion pendiente de una entrada anticipada.
     *
     * @param idAsistencia jornada sobre la que se registrara la entrada
     * @param instanteEscaneo hora del PRIMER escaneo. Es la que se
     *        registra al confirmar: el trabajador llego cuando paso su
     *        carne la primera vez; el segundo escaneo solo confirma la
     *        intencion, no marca una llegada distinta.
     * @param expiraEn limite de la ventana de confirmacion
     */
    public record Pendiente(Long idAsistencia,
                            LocalDateTime instanteEscaneo,
                            LocalDateTime expiraEn) {}

    // ============================================================
    // Anti-rebote
    // ============================================================

    /**
     * true si el escaneo debe descartarse por llegar demasiado pronto
     * tras el anterior.
     *
     * No aplica cuando hay una confirmacion pendiente: en ese caso el
     * segundo escaneo es precisamente lo que el sistema esta esperando.
     */
    public boolean esRebote(Long idTrabajador, LocalDateTime ahora, int intervaloSeg) {
        if (tienePendiente(idTrabajador, ahora)) return false;

        LocalDateTime ultimo = ultimoEscaneo.get(idTrabajador);
        if (ultimo == null) return false;

        return ultimo.plusSeconds(intervaloSeg).isAfter(ahora);
    }

    public void registrarEscaneo(Long idTrabajador, LocalDateTime instante) {
        ultimoEscaneo.put(idTrabajador, instante);
    }

    // ============================================================
    // Confirmacion por doble escaneo
    // ============================================================

    public void abrirConfirmacion(Long idTrabajador, Long idAsistencia,
                                  LocalDateTime instante, int ventanaSeg) {
        pendientes.put(idTrabajador, new Pendiente(
                idAsistencia, instante, instante.plusSeconds(ventanaSeg)));
        log.debug("Confirmacion abierta para trabajador {} hasta {}",
                idTrabajador, instante.plusSeconds(ventanaSeg));
    }

    public boolean tienePendiente(Long idTrabajador, LocalDateTime ahora) {
        Pendiente p = pendientes.get(idTrabajador);
        if (p == null) return false;
        if (ahora.isAfter(p.expiraEn())) {
            // Vencida sin confirmar: no se guarda nada (HU-22, criterio 3)
            pendientes.remove(idTrabajador);
            return false;
        }
        return true;
    }

    /** Consume la confirmacion pendiente. Devuelve null si no hay o vencio. */
    public Pendiente consumirPendiente(Long idTrabajador, LocalDateTime ahora) {
        if (!tienePendiente(idTrabajador, ahora)) return null;
        return pendientes.remove(idTrabajador);
    }

    public void cancelarPendiente(Long idTrabajador) {
        pendientes.remove(idTrabajador);
    }

    /** Limpieza defensiva, para que los mapas no crezcan sin limite. */
    public void purgar(LocalDateTime ahora) {
        pendientes.entrySet().removeIf(e -> ahora.isAfter(e.getValue().expiraEn()));
        ultimoEscaneo.entrySet().removeIf(e -> e.getValue().plusHours(24).isBefore(ahora));
    }
}
