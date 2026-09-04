package com.idat.asistencia.service.impl;

import com.idat.asistencia.model.entity.Asistencia;
import com.idat.asistencia.model.enums.EstadoAsistencia;
import com.idat.asistencia.model.enums.TipoRegistro;
import com.idat.asistencia.repository.AsistenciaRepository;
import com.idat.asistencia.service.AuditoriaService;
import com.idat.asistencia.service.CierreDiarioService;
import com.idat.asistencia.service.FeriadoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cierre automatico de jornadas vencidas (CU29, RN-42).
 *
 * ============================================================
 * POR QUE ESTE PROCESO EXISTE
 * ============================================================
 * Los tipos FALTA_INJUSTIFICADA y MARCACION_INCOMPLETA estaban definidos
 * en el modelo pero NINGUN proceso los producia. Una ausencia no dejaba
 * rastro alguno: el pre-registro se quedaba en PENDIENTE para siempre, no
 * aparecia en ningun reporte, y ademas bloqueaba el cierre de la quincena
 * sin que nadie supiera por que.
 *
 * ============================================================
 * FRECUENCIA HORARIA, NO DIARIA
 * ============================================================
 * Se ejecuta cada hora y no una vez al dia porque con dos turnos en
 * paralelo (RT-09) las jornadas vencen en momentos muy distintos: la
 * diurna a media tarde, la nocturna a media manana del dia siguiente.
 * Esperar a un horario fijo dejaria la bandeja de pendientes y la vista
 * de planta desactualizadas durante horas.
 *
 * ============================================================
 * NUNCA INVENTA UNA HORA DE SALIDA
 * ============================================================
 * Una jornada con entrada y sin salida se marca como incompleta y se deja
 * para el Jefe (CU15). Deducir la salida del horario programado
 * introduciria un dato que nadie registro en un sistema cuyo proposito es
 * justamente tener registros verificables.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CierreDiarioServiceImpl implements CierreDiarioService {

    private final AsistenciaRepository asistenciaRepo;
    private final FeriadoService       feriadoService;
    private final AuditoriaService     auditoria;

    private static final String TABLA = "asistencias";

    /**
     * Cada hora, al minuto 5. El desfase evita coincidir con otros
     * procesos que suelan arrancar en punto.
     */
    @Scheduled(cron = "0 5 * * * *")
    public void ejecutarProgramado() {
        try {
            ResultadoCierre r = ejecutar();
            if (r.total() > 0) {
                log.info("Cierre diario: {} faltas, {} incompletas, {} cubiertas por ausencia",
                        r.faltasInjustificadas(), r.marcacionesIncompletas(),
                        r.cubiertasPorAusencia());
            }
        } catch (Exception e) {
            // Un fallo del proceso programado no debe tumbar la
            // aplicacion ni impedir la siguiente ejecucion.
            log.error("Error en el cierre diario de jornadas: {}", e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public ResultadoCierre ejecutar() {
        LocalDateTime ahora = LocalDateTime.now();

        List<Asistencia> vencidas = asistenciaRepo.findJornadasVencidasSinResolver(ahora);
        if (vencidas.isEmpty()) return new ResultadoCierre(0, 0, 0, 0);

        int faltas = 0, incompletas = 0, cubiertas = 0;

        for (Asistencia a : vencidas) {

            if (a.getEstado() == EstadoAsistencia.PENDIENTE) {
                // Sin marcacion alguna.

                boolean cubierta = a.tieneAusenciaJustificada()
                        || feriadoService.esFeriado(a.getFecha());

                if (cubierta) {
                    // Permiso, falta justificada o feriado: se cierra sin
                    // generar falta (RN-44).
                    a.setEstado(EstadoAsistencia.REVISADO);
                    a.setRequiereRevision(false);
                    a.setRevisadoEn(ahora);
                    if (!a.isEsDiaNoLaborable() && feriadoService.esFeriado(a.getFecha()))
                        a.setEsDiaNoLaborable(true);
                    cubiertas++;

                } else {
                    a.setTipo(TipoRegistro.FALTA_INJUSTIFICADA);
                    // Queda en REVISADO, no en CALCULADO: es un hecho
                    // decidido y no una decision pendiente. Si bloqueara
                    // el cierre, la falta de un solo trabajador impediria
                    // consolidar la quincena de los otros setenta y nueve.
                    // Sigue visible en la bandeja para que el Jefe la
                    // reclasifique mientras la quincena este abierta.
                    a.setEstado(EstadoAsistencia.REVISADO);
                    a.setRequiereRevision(false);
                    a.setRevisadoEn(ahora);
                    a.setMinHorasTotales(0);
                    faltas++;
                }

            } else if (a.getEstado() == EstadoAsistencia.MARCADO) {
                // Entrada sin salida. NO se infiere la hora de salida.
                a.setTipo(TipoRegistro.MARCACION_INCOMPLETA);
                a.setEstado(EstadoAsistencia.CALCULADO);
                a.setRequiereRevision(true);
                a.setObservacion(concatenar(a.getObservacion(),
                        "Cierre automatico: no se registro la salida."));
                incompletas++;
            }

            asistenciaRepo.save(a);
        }

        int total = faltas + incompletas + cubiertas;

        // Un evento por ejecucion, no uno por registro: una corrida puede
        // afectar decenas de filas y llenar la auditoria de ruido.
        auditoria.registrarCampo(TABLA, null, "CIERRE_DIARIO", "resumen", null,
                total + " jornadas resueltas: " + faltas + " faltas injustificadas, "
                        + incompletas + " marcaciones incompletas, "
                        + cubiertas + " cubiertas por ausencia o feriado");

        return new ResultadoCierre(faltas, incompletas, cubiertas, total);
    }

    private String concatenar(String previo, String nuevo) {
        if (previo == null || previo.isBlank()) return nuevo;
        if (previo.contains(nuevo)) return previo;
        return previo + " | " + nuevo;
    }
}
