package com.idat.asistencia.service.impl;

import com.idat.asistencia.dto.DashboardDTOs.*;
import com.idat.asistencia.model.entity.Area;
import com.idat.asistencia.model.entity.Asistencia;
import com.idat.asistencia.model.entity.Trabajador;
import com.idat.asistencia.model.enums.EstadoAsistencia;
import com.idat.asistencia.model.enums.ResultadoValidacion;
import com.idat.asistencia.model.enums.TipoRegistro;
import com.idat.asistencia.repository.AreaRepository;
import com.idat.asistencia.repository.AsistenciaRepository;
import com.idat.asistencia.security.SecurityHelper;
import com.idat.asistencia.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Estadisticas para la toma de decisiones.
 *
 * ============================================================
 * POR QUE SE AGREGA EN MEMORIA Y NO EN SQL
 * ============================================================
 * Una sola consulta trae las jornadas del periodo y aqui se calculan los
 * seis cortes. La alternativa, seis consultas de agregacion en JPQL,
 * seria mas rapida en teoria pero con un volumen de unas 2400 filas al
 * mes la diferencia es irrelevante, y en cambio obligaria a mantener seis
 * consultas que deben coincidir entre si en los criterios.
 *
 * Ese es el punto: los criterios NO son obvios y conviene que vivan en un
 * solo sitio. Que una falta no cuente para la puntualidad, que la hora
 * extra solo cuente si esta aprobada, que un permiso no sea una ausencia
 * injustificada. Repartidos en seis consultas, cualquier ajuste futuro se
 * aplicaria a unas y no a otras, y los numeros dejarian de cuadrar entre
 * tarjetas.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final AsistenciaRepository asistenciaRepo;
    private final AreaRepository       areaRepo;
    private final SecurityHelper       securityHelper;

    /** Cuantas filas devuelven los rankings. */
    private static final int TOPE_RANKING = 10;

    @Override
    public EstadisticasResponse calcular(LocalDate desde, LocalDate hasta, Integer idArea) {

        // El Jefe solo ve su area, aunque pida otra (RN-01).
        if (securityHelper.esJefe()) {
            idArea = securityHelper.getIdAreaJefeAutenticado();
        }

        List<Asistencia> jornadas = asistenciaRepo.findReporte(desde, hasta, null, idArea);

        var b = EstadisticasResponse.builder()
                .desde(desde.toString())
                .hasta(hasta.toString())
                .diasPeriodo((int) java.time.temporal.ChronoUnit.DAYS.between(desde, hasta) + 1)
                .areaNombre(idArea == null ? null
                        : areaRepo.findById(idArea).map(Area::getArea).orElse(null));

        acumularIndicadores(jornadas, b);

        b.tendenciaDiaria(tendencia(jornadas, desde, hasta));
        b.porArea(cortePorArea(jornadas));
        b.porTurno(cortePorTurno(jornadas));
        b.sobrecarga(sobrecarga(jornadas));
        b.rankingTardanzas(rankingTardanzas(jornadas));
        b.rankingFaltas(rankingFaltas(jornadas));

        return b.build();
    }

    // ════════════════════════════════════════════════════════════
    // INDICADORES
    // ════════════════════════════════════════════════════════════

    private void acumularIndicadores(List<Asistencia> jornadas, EstadisticasResponse.EstadisticasResponseBuilder b) {
        int trabajadas = 0, tardanzas = 0, minTardanza = 0;
        int faltas = 0, permisos = 0, faltasJust = 0;
        int minTrab = 0, minEsp = 0, minExtraOk = 0, minExtraPend = 0, minFeriado = 0;
        int pendientes = 0, incompletas = 0;

        // Trabajadores sin ninguna tardanza ni falta en el periodo.
        Map<Long, Boolean> perfectos = new HashMap<>();

        for (Asistencia a : jornadas) {
            Long idT = a.getTrabajador().getIdTrabajador();
            perfectos.putIfAbsent(idT, true);

            if (a.getTipo() == TipoRegistro.FALTA_INJUSTIFICADA) {
                faltas++;
                perfectos.put(idT, false);
                continue;
            }

            // Un permiso NO es una ausencia injustificada, y contarlo como
            // tal castigaria a quien tiene una ausencia aprobada.
            if (a.getIngresoReal() == null) {
                if (a.getPermiso()          != null) permisos++;
                if (a.getFaltaJustificada() != null) faltasJust++;
                continue;
            }

            trabajadas++;
            minTrab += nz(a.getMinHorasTotales());
            minEsp  += nz(a.getMinNetosProg()) + nz(a.getMinExtraProg());
            minFeriado += nz(a.getMinutosFeriado());

            int tard = nz(a.getMinTardanza());
            if (tard > 0) {
                tardanzas++;
                minTardanza += tard;
                perfectos.put(idT, false);
            }

            // La hora extra solo cuenta si fue aprobada (RN-33). La
            // pendiente se reporta aparte: es trabajo hecho que todavia
            // no se reconoce, y verlo acumularse es la senal de que la
            // bandeja de revision se esta quedando atras.
            int extraEstructural = nz(a.getMinExtraProg());
            int extraExcepcional = nz(a.getValMinPrevIng()) + nz(a.getValMinPostSal());

            if (a.getResultadoValidacion() == ResultadoValidacion.APROBADO) {
                minExtraOk += extraEstructural + extraExcepcional;
            } else {
                minExtraOk += extraEstructural;
                if (a.isRequiereRevision()) {
                    minExtraPend += Math.max(0,
                            nz(a.getMinPrevIngProg()) + nz(a.getMinPostSalProg()));
                }
            }

            if (a.isRequiereRevision()) pendientes++;
            if (a.getTipo() == TipoRegistro.MARCACION_INCOMPLETA) incompletas++;
        }

        int totalJornadas = jornadas.size();
        long perfectosCount = perfectos.values().stream().filter(Boolean::booleanValue).count();

        // Sobre las TRABAJADAS, no sobre el total: una falta no es una
        // impuntualidad, y mezclarlas haria que un dia con muchas
        // ausencias justificadas pareciera un problema de puntualidad.
        double tasa = trabajadas == 0 ? 0
                : redondear(100.0 * (trabajadas - tardanzas) / trabajadas);

        b.totalJornadas(totalJornadas)
         .jornadasTrabajadas(trabajadas)
         .tasaPuntualidad(tasa)
         .totalTardanzas(tardanzas)
         .minutosTardanza(minTardanza)
         .faltasInjustificadas(faltas)
         .diasPermiso(permisos)
         .diasFaltaJustificada(faltasJust)
         .asistenciasPerfectas((int) perfectosCount)
         .trabajadoresConJornadas(perfectos.size())
         .minutosTrabajados(minTrab)
         .minutosEsperados(minEsp)
         .minutosExtraReconocidos(minExtraOk)
         .minutosExtraPendientes(minExtraPend)
         .minutosFeriado(minFeriado)
         .horasTrabajadas(fmt(minTrab))
         .horasExtra(fmt(minExtraOk))
         .horasFeriado(fmt(minFeriado))
         .pendientesRevision(pendientes)
         .marcacionesIncompletas(incompletas);
    }

    // ════════════════════════════════════════════════════════════
    // TENDENCIA DIARIA
    // ════════════════════════════════════════════════════════════

    /**
     * Un punto por dia del periodo, incluidos los dias sin jornadas.
     *
     * Los huecos importan: una serie que salta del lunes al miercoles
     * oculta que el martes no vino nadie, que es justo lo que hay que ver.
     */
    private List<PuntoDiario> tendencia(List<Asistencia> jornadas,
                                        LocalDate desde, LocalDate hasta) {
        Map<LocalDate, List<Asistencia>> porDia = jornadas.stream()
                .collect(Collectors.groupingBy(Asistencia::getFecha));

        List<PuntoDiario> puntos = new ArrayList<>();
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
            List<Asistencia> delDia = porDia.getOrDefault(d, List.of());

            int trabajadas = 0, tardanzas = 0, faltas = 0, minutos = 0;
            boolean feriado = false;

            for (Asistencia a : delDia) {
                if (a.isEsDiaNoLaborable()) feriado = true;
                if (a.getTipo() == TipoRegistro.FALTA_INJUSTIFICADA) { faltas++; continue; }
                if (a.getIngresoReal() == null) continue;
                trabajadas++;
                minutos += nz(a.getMinHorasTotales());
                if (nz(a.getMinTardanza()) > 0) tardanzas++;
            }

            String dia = d.getDayOfWeek().getDisplayName(
                    TextStyle.SHORT, java.util.Locale.forLanguageTag("es-PE"));

            puntos.add(PuntoDiario.builder()
                    .fecha(d.toString())
                    .diaSemana(dia)
                    .programadas(delDia.size())
                    .trabajadas(trabajadas)
                    .tardanzas(tardanzas)
                    .faltas(faltas)
                    .minutosTrabajados(minutos)
                    .esFeriado(feriado)
                    .build());
        }
        return puntos;
    }

    // ════════════════════════════════════════════════════════════
    // CORTES
    // ════════════════════════════════════════════════════════════

    private List<CorteArea> cortePorArea(List<Asistencia> jornadas) {
        Map<String, List<Asistencia>> porArea = jornadas.stream()
                .collect(Collectors.groupingBy(a -> {
                    Area ar = a.getTrabajador().getArea();
                    return ar != null ? ar.getArea() : "(sin area)";
                }));

        return porArea.entrySet().stream().map(e -> {
            List<Asistencia> lista = e.getValue();
            int jorn = 0, tard = 0, falt = 0, minT = 0, minE = 0;
            Set<Long> trabajadores = new HashSet<>();

            for (Asistencia a : lista) {
                trabajadores.add(a.getTrabajador().getIdTrabajador());
                if (a.getTipo() == TipoRegistro.FALTA_INJUSTIFICADA) { falt++; continue; }
                if (a.getIngresoReal() == null) continue;
                jorn++;
                minT += nz(a.getMinHorasTotales());
                minE += nz(a.getMinExtraProg());
                if (a.getResultadoValidacion() == ResultadoValidacion.APROBADO)
                    minE += nz(a.getValMinPrevIng()) + nz(a.getValMinPostSal());
                if (nz(a.getMinTardanza()) > 0) tard++;
            }

            Integer idArea = lista.stream()
                    .map(a -> a.getTrabajador().getArea())
                    .filter(Objects::nonNull)
                    .map(Area::getIdArea)
                    .findFirst().orElse(null);

            return CorteArea.builder()
                    .idArea(idArea)
                    .area(e.getKey())
                    .trabajadores(trabajadores.size())
                    .jornadas(jorn)
                    .tardanzas(tard)
                    .faltas(falt)
                    .minutosTrabajados(minT)
                    .minutosExtra(minE)
                    .tasaPuntualidad(jorn == 0 ? 0 : redondear(100.0 * (jorn - tard) / jorn))
                    .horasTrabajadas(fmt(minT))
                    .build();
        }).sorted(Comparator.comparing(CorteArea::getArea)).collect(Collectors.toList());
    }

    private List<CorteTurno> cortePorTurno(List<Asistencia> jornadas) {
        Map<String, List<Asistencia>> porTurno = jornadas.stream()
                .filter(a -> a.getIngresoReal() != null)
                .collect(Collectors.groupingBy(a ->
                        a.getTurno() != null ? a.getTurno().getNombre() : "(sin turno)"));

        return porTurno.entrySet().stream().map(e -> {
            List<Asistencia> lista = e.getValue();
            int jorn = 0, tard = 0, minN = 0, minE = 0, minF = 0;

            for (Asistencia a : lista) {
                jorn++;
                int total = nz(a.getMinHorasTotales());
                int extra = nz(a.getMinExtraProg());
                if (a.getResultadoValidacion() == ResultadoValidacion.APROBADO)
                    extra += nz(a.getValMinPrevIng()) + nz(a.getValMinPostSal());
                extra = Math.min(extra, total);

                minN += total - extra;
                minE += extra;
                minF += nz(a.getMinutosFeriado());
                if (nz(a.getMinTardanza()) > 0) tard++;
            }

            return CorteTurno.builder()
                    .turno(e.getKey())
                    .jornadas(jorn)
                    .tardanzas(tard)
                    .minutosNormales(minN)
                    .minutosExtra(minE)
                    .minutosFeriado(minF)
                    .tasaPuntualidad(jorn == 0 ? 0 : redondear(100.0 * (jorn - tard) / jorn))
                    .horasNormales(fmt(minN))
                    .horasExtra(fmt(minE))
                    .build();
        }).sorted(Comparator.comparing(CorteTurno::getTurno)).collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════
    // RANKINGS
    // ════════════════════════════════════════════════════════════

    /** Acumulado por trabajador, base de los tres rankings. */
    private Map<Long, FilaTrabajador.FilaTrabajadorBuilder> acumularPorTrabajador(List<Asistencia> jornadas) {
        Map<Long, int[]> datos = new HashMap<>();   // [jorn, minT, minE, tard, minTard, faltas]
        Map<Long, Trabajador> refs = new HashMap<>();

        for (Asistencia a : jornadas) {
            Long id = a.getTrabajador().getIdTrabajador();
            refs.putIfAbsent(id, a.getTrabajador());
            int[] d = datos.computeIfAbsent(id, k -> new int[6]);

            if (a.getTipo() == TipoRegistro.FALTA_INJUSTIFICADA) { d[5]++; continue; }
            if (a.getIngresoReal() == null) continue;

            d[0]++;
            d[1] += nz(a.getMinHorasTotales());
            d[2] += nz(a.getMinNetosProg()) + nz(a.getMinExtraProg());
            int t = nz(a.getMinTardanza());
            if (t > 0) { d[3]++; d[4] += t; }
        }

        Map<Long, FilaTrabajador.FilaTrabajadorBuilder> salida = new HashMap<>();
        datos.forEach((id, d) -> {
            Trabajador t = refs.get(id);
            salida.put(id, FilaTrabajador.builder()
                    .idTrabajador(id)
                    .nombre(t.getNombreCompleto())
                    .area(t.getArea() != null ? t.getArea().getArea() : "—")
                    .puesto(t.getPuesto() != null ? t.getPuesto().getPuesto() : "—")
                    .jornadas(d[0])
                    .minutosTrabajados(d[1])
                    .minutosEsperados(d[2])
                    .saldoMinutos(d[1] - d[2])
                    .saldoHoras(fmtConSigno(d[1] - d[2]))
                    .tardanzas(d[3])
                    .minutosTardanza(d[4])
                    .faltas(d[5])
                    .horasTrabajadas(fmt(d[1])));
        });
        return salida;
    }

    /**
     * Trabajadores con mas horas por encima de lo esperado.
     *
     * Se ordena por saldo y no por horas totales: quien trabaja mas
     * porque su turno es mas largo no esta sobrecargado, lo esta quien
     * excede lo que se le programo.
     */
    private List<FilaTrabajador> sobrecarga(List<Asistencia> jornadas) {
        return acumularPorTrabajador(jornadas).values().stream()
                .map(FilaTrabajador.FilaTrabajadorBuilder::build)
                .filter(f -> f.getSaldoMinutos() > 0)
                .sorted(Comparator.comparingInt(FilaTrabajador::getSaldoMinutos).reversed())
                .limit(TOPE_RANKING)
                .collect(Collectors.toList());
    }

    private List<FilaTrabajador> rankingTardanzas(List<Asistencia> jornadas) {
        return acumularPorTrabajador(jornadas).values().stream()
                .map(FilaTrabajador.FilaTrabajadorBuilder::build)
                .filter(f -> f.getTardanzas() > 0)
                .sorted(Comparator.comparingInt(FilaTrabajador::getMinutosTardanza).reversed())
                .limit(TOPE_RANKING)
                .collect(Collectors.toList());
    }

    private List<FilaTrabajador> rankingFaltas(List<Asistencia> jornadas) {
        return acumularPorTrabajador(jornadas).values().stream()
                .map(FilaTrabajador.FilaTrabajadorBuilder::build)
                .filter(f -> f.getFaltas() > 0)
                .sorted(Comparator.comparingInt(FilaTrabajador::getFaltas).reversed())
                .limit(TOPE_RANKING)
                .collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════

    private int nz(Integer v) { return v != null ? v : 0; }

    private double redondear(double v) { return Math.round(v * 10.0) / 10.0; }

    private String fmt(int min) {
        return String.format("%02d:%02d", min / 60, Math.abs(min % 60));
    }

    private String fmtConSigno(int min) {
        return (min < 0 ? "-" : "+") + fmt(Math.abs(min));
    }
}
