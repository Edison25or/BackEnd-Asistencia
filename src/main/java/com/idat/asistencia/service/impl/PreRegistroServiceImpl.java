package com.idat.asistencia.service.impl;

import com.idat.asistencia.exception.BusinessException;
import com.idat.asistencia.model.entity.*;
import com.idat.asistencia.model.enums.EstadoAsistencia;
import com.idat.asistencia.model.enums.EstadoQuincena;
import com.idat.asistencia.model.enums.TipoRegistro;
import com.idat.asistencia.repository.AsistenciaRepository;
import com.idat.asistencia.repository.HorarioDiaRepository;
import com.idat.asistencia.repository.QuincenaRepository;
import com.idat.asistencia.service.AusenciaService;
import com.idat.asistencia.service.FeriadoService;
import com.idat.asistencia.service.ParametrosService;
import com.idat.asistencia.service.PreRegistroService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generacion de pre-registros de asistencia y resolucion de quincena
 * (CU14, RN-35).
 *
 * ============================================================
 * POR QUE ESTE SERVICIO EXISTE
 * ============================================================
 * El prototipo tenia la generacion de pre-registros DUPLICADA:
 * AsistenciaServiceImpl.generarPreRegistros() y la logica embebida en
 * ProgramacionSemanalServiceImpl.confirmarSemana(). La primera ni
 * siquiera estaba declarada en la interfaz AsistenciaService, de modo que
 * nadie la invocaba: era codigo muerto y la unica copia viva era la
 * embebida. Aqui queda una sola implementacion.
 *
 * ============================================================
 * LA QUINCENA SE RESUELVE POR DIA, NO POR SEMANA
 * ============================================================
 * confirmarSemana() tomaba el sabado de inicio y resolvia UNA quincena
 * para toda la semana. Con cortes al 15 y a fin de mes, cualquier semana
 * que cruce el corte pertenece a DOS quincenas, y el campo quincena de
 * Asistencia es obligatorio.
 *
 * Ademas, si no encontraba quincena lanzaba una excepcion pidiendo
 * crearla a mano. Ahora se autogenera.
 *
 * La atribucion se ancla a la HORA DE ENTRADA PROGRAMADA y no a la fecha
 * calendario, en coherencia con RN-24: una jornada nocturna que entra el
 * dia 15 a las 22:00, despues del corte de las 18:00, pertenece a la
 * segunda quincena aunque termine el 16.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreRegistroServiceImpl implements PreRegistroService {

    private final AsistenciaRepository   asistenciaRepo;
    private final HorarioDiaRepository   horarioDiaRepo;
    private final QuincenaRepository     quincenaRepo;
    private final ParametrosService      parametrosService;
    private final FeriadoService         feriadoService;
    private final AusenciaService        ausenciaService;

    // ============================================================
    // GENERACION DE PRE-REGISTROS
    // ============================================================

    @Override
    @Transactional
    public ResultadoGeneracion generar(List<ProgramacionSemanal> programaciones,
                                       LocalDate inicioSemana, LocalDate finSemana) {

        ParametrosGeneralesAsistencia params = parametrosService.getGenerales();
        ParametrosQuincena            pq     = parametrosService.getQuincena();

        // Cache por transaccion: sin ella se consultaria la quincena una
        // vez por trabajador y por dia, unas 560 veces en una semana de
        // 80 trabajadores.
        Map<LocalDate, Quincena> cacheQuincena = new HashMap<>();

        int creados = 0, omitidos = 0, enFeriado = 0;

        for (ProgramacionSemanal prog : programaciones) {
            Trabajador     t = prog.getTrabajador();
            EsquemaHorario e = prog.getEsquema();

            for (LocalDate dia = inicioSemana; !dia.isAfter(finSemana); dia = dia.plusDays(1)) {

                HorarioDia hd = horarioDiaRepo
                        .findByEsquemaAndDia(e.getIdEsquema(), dia.getDayOfWeek().getValue())
                        .orElse(null);

                // Sin horario o dia de descanso: no hay jornada que crear
                if (hd == null || Boolean.TRUE.equals(hd.getEsDescanso())) continue;

                if (asistenciaRepo.existsByTrabajador_IdTrabajadorAndFechaAndTipo(
                        t.getIdTrabajador(), dia, TipoRegistro.PROGRAMADA)) {
                    omitidos++;
                    continue;
                }

                // ---- Tiempos programados como fecha y hora ----
                LocalDateTime ingresoProg = dia.atTime(hd.getHoraEntrada());
                int duracion = nz(hd.getMinutosNetos())
                             + nz(hd.getMinutosRefrigerio())
                             + nz(hd.getMinutosExtraProgramado());
                LocalDateTime salidaProg = ingresoProg.plusMinutes(duracion);
                // La salida de un turno nocturno cae en el dia calendario
                // siguiente de forma natural, sin reglas adicionales.

                // ---- Quincena, resuelta por la hora de entrada ----
                Quincena q = cacheQuincena.computeIfAbsent(dia,
                        d -> resolverOCrear(ingresoProg, pq));

                if (q.getEstado() == EstadoQuincena.CERRADA) {
                    log.warn("Dia {} pertenece a la quincena cerrada {}; se omite el pre-registro",
                            dia, q.getDescripcion());
                    omitidos++;
                    continue;
                }

                boolean esFeriado = feriadoService.esFeriado(dia);
                if (esFeriado) enFeriado++;

                Asistencia pre = Asistencia.builder()
                        .trabajador(t)
                        .fecha(dia)
                        .tipo(TipoRegistro.PROGRAMADA)
                        .estado(EstadoAsistencia.PENDIENTE)
                        .requiereRevision(false)
                        .esquema(e)
                        .turno(e.getTurno())
                        .programacion(prog)
                        .quincena(q)
                        .esDiaNoLaborable(esFeriado)
                        .ingresoProg(ingresoProg)
                        .salidaProg(salidaProg)
                        .minRefrigerioProg(nz(hd.getMinutosRefrigerio()))
                        .minNetosProg(nz(hd.getMinutosNetos()))
                        .minExtraProg(nz(hd.getMinutosExtraProgramado()))
                        .build();

                // La ventana es lo que permite localizar la jornada al
                // marcar, incluso cuando la salida ocurre al dia
                // calendario siguiente.
                pre.recalcularVentana(
                        e.getToleranciaPrevia(), e.getToleranciaPosterior(),
                        params.getMaxAnticipacionEntrada(), params.getMaxExcesoSalida());

                asistenciaRepo.save(pre);
                creados++;
            }

            // Ausencias registradas ANTES de programar la semana. Sin
            // esto, un permiso pedido con dos semanas de anticipacion
            // quedaria sin efecto sobre los pre-registros que se acaban
            // de crear, y el cierre diario reportaria falta (RN-44).
            ausenciaService.neutralizarPorAusenciasExistentes(
                    t.getIdTrabajador(), inicioSemana, finSemana);
        }

        return new ResultadoGeneracion(creados, omitidos, enFeriado,
                cacheQuincena.values().stream().distinct().toList());
    }

    // ============================================================
    // RESOLUCION DE QUINCENA
    // ============================================================

    @Override
    @Transactional
    public Quincena resolverOCrear(LocalDateTime instante, ParametrosQuincena pq) {
        return quincenaRepo.findQueContiene(instante)
                .orElseGet(() -> crear(instante, pq));
    }

    @Override
    @Transactional
    public Quincena resolverOCrear(LocalDateTime instante) {
        return resolverOCrear(instante, parametrosService.getQuincena());
    }

    /**
     * Crea la quincena que contiene el instante dado.
     *
     * Un instante puede caer en tres lugares distintos segun la hora de
     * corte, y los tres deben resolverse bien:
     *
     *   dia 3 a las 08:00   -> primera quincena del mes
     *   dia 15 a las 17:00  -> primera quincena (aun antes del corte)
     *   dia 15 a las 22:00  -> segunda quincena (ya paso el corte)
     *   dia 31 a las 22:00  -> primera quincena del mes SIGUIENTE
     */
    private Quincena crear(LocalDateTime instante, ParametrosQuincena pq) {
        int anio = instante.getYear();
        int mes  = instante.getMonthValue();

        // Se prueban las candidatas del mes anterior, el actual y el
        // siguiente, porque el corte del ultimo dia del mes hace que un
        // instante de fin de mes pertenezca ya al periodo siguiente.
        LocalDate base = LocalDate.of(anio, mes, 1);
        for (LocalDate m : List.of(base.minusMonths(1), base, base.plusMonths(1))) {
            for (int numero : new int[]{1, 2}) {
                LocalDateTime ini = pq.inicioDe(m.getYear(), m.getMonthValue(), numero);
                LocalDateTime fin = pq.finDe(m.getYear(), m.getMonthValue(), numero);

                if (!instante.isBefore(ini) && instante.isBefore(fin)) {
                    // Otra transaccion pudo haberla creado ya
                    return quincenaRepo.findByAnioAndMesAndNumero(
                                    m.getYear(), m.getMonthValue(), numero)
                            .orElseGet(() -> quincenaRepo.save(Quincena.builder()
                                    .anio(m.getYear())
                                    .mes(m.getMonthValue())
                                    .numero(numero)
                                    .inicio(ini)
                                    .fin(fin)
                                    .estado(EstadoQuincena.ABIERTA)
                                    .build()));
                }
            }
        }

        throw new BusinessException(
                "No se pudo determinar la quincena para " + instante
                        + ". Revisa los parametros de corte configurados.");
    }

    private int nz(Integer v) {
        return v != null ? v : 0;
    }
}
