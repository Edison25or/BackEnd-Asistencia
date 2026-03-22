package com.idat.asistencia.service.impl;

import com.idat.asistencia.dto.ConsolidadoDTOs.*;
import com.idat.asistencia.dto.ConsolidadoDTOs.BolsaHistorialDTO;
import com.idat.asistencia.dto.ConsolidadoDTOs.ConsolidadoReporteResponse;
import com.idat.asistencia.exception.BusinessException;
import com.idat.asistencia.exception.ResourceNotFoundException;
import com.idat.asistencia.model.entity.*;
import com.idat.asistencia.model.entity.Usuario;
import com.idat.asistencia.model.enums.EstadoAsistencia;
import com.idat.asistencia.model.enums.EstadoQuincena;
import com.idat.asistencia.model.enums.TipoAsistencia;
import com.idat.asistencia.repository.*;
import com.idat.asistencia.service.AuditoriaService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.idat.asistencia.service.ConsolidadoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsolidadoServiceImpl implements ConsolidadoService {

    private final ConsolidadoRepository  consolidadoRepo;
    private final QuincenaRepository     quincenaRepo;
    private final AsistenciaRepository   asistenciaRepo;
    private final TrabajadorRepository   trabajadorRepo;
    private final UsuarioRepository      usuarioRepo;
    private final AuditoriaService       auditoriaService;

    // Tope diario en minutos para tasa A (2 horas = 120 min por normativa peruana)
    private static final int TOPE_TASA_A_MIN = 120;

    // ════════════════════════════════════════════════════════════
    // GENERAR CONSOLIDADO
    // ════════════════════════════════════════════════════════════
    @Override
    @Transactional
    public List<ConsolidadoResponse> generarConsolidado(Long idQuincena) {
        Quincena q = buscarQuincena(idQuincena);

        if (q.getEstado() == EstadoQuincena.CERRADA)
            throw new BusinessException(
                    "La quincena «" + q.getDescripcion() + "» ya está cerrada.");

        // Obtener todas las asistencias REVISADAS de la quincena
        List<Asistencia> asistencias = asistenciaRepo.findByQuincena(idQuincena)
                .stream()
                .filter(a -> a.getEstado() == EstadoAsistencia.REVISADO
                        || a.getEstado() == EstadoAsistencia.CONSOLIDADO)
                .collect(Collectors.toList());

        if (asistencias.isEmpty())
            throw new BusinessException(
                    "No hay asistencias revisadas en esta quincena. " +
                            "Revisa las asistencias antes de generar el consolidado.");

        // Agrupar por trabajador
        Map<Long, List<Asistencia>> porTrabajador = asistencias.stream()
                .collect(Collectors.groupingBy(a -> a.getTrabajador().getIdTrabajador()));

        List<ConsolidadoResponse> resultado = new ArrayList<>();

        for (Map.Entry<Long, List<Asistencia>> entry : porTrabajador.entrySet()) {
            Long           idTrab  = entry.getKey();
            List<Asistencia> asis  = entry.getValue();

            ConsolidadoQuincena consolidado = consolidadoRepo
                    .findByQuincena_IdQuincenaAndTrabajador_IdTrabajador(idQuincena, idTrab)
                    .orElse(null);

            // Calcular saldo de bolsa anterior
            int bolsaEntrada = 0;
            if (consolidado == null) {
                // Primera vez: buscar la bolsa_salida del último consolidado cerrado
                List<ConsolidadoQuincena> anteriores =
                        consolidadoRepo.findUltimosCerradosByTrabajador(idTrab, idQuincena);
                if (!anteriores.isEmpty())
                    bolsaEntrada = anteriores.get(0).getBolsaSalida();
            } else {
                bolsaEntrada = consolidado.getBolsaEntrada();
            }

            ConsolidadoQuincena calc = calcularParaTrabajador(
                    q, asis, idTrab, bolsaEntrada, consolidado);

            resultado.add(toResponse(consolidadoRepo.save(calc)));
        }

        auditoriaService.registrar("consolidado_quincena", idQuincena, "GENERAR");
        return resultado;
    }

    // ════════════════════════════════════════════════════════════
    // CÁLCULO POR TRABAJADOR
    // ════════════════════════════════════════════════════════════
    private ConsolidadoQuincena calcularParaTrabajador(
            Quincena quincena,
            List<Asistencia> asis,
            Long idTrabajador,
            int bolsaEntrada,
            ConsolidadoQuincena existente) {

        Trabajador trabajador = trabajadorRepo.findById(idTrabajador)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado."));

        int minNormalesDia   = 0, minNormalesNoche = 0;
        int minExtraDiaA     = 0, minExtraNocheA   = 0;
        int minExtraDiaB     = 0, minExtraNocheB   = 0;
        int minDiaDesc       = 0, minNocheDesc     = 0;
        int minTardanza      = 0, minSalTemprana   = 0;
        int diasFalta        = 0, diasPermiso      = 0;

        for (Asistencia a : asis) {
            boolean nocturno = a.isEsNocturno();

            switch (a.getTipo()) {
                case FALTA -> {
                    // Las faltas descontables se calculan desde los minutos programados
                    int minProg = a.getMinNetosProg() != null ? a.getMinNetosProg() : 0;
                    if (nocturno) minNocheDesc += minProg;
                    else          minDiaDesc   += minProg;
                    diasFalta++;
                }
                case PERMISO -> diasPermiso++;

                default -> {
                    // Horas trabajadas normales
                    int minTrab = a.getMinHorasTotales() != null ? a.getMinHorasTotales() : 0;
                    int minProg = a.getMinNetosProg()    != null ? a.getMinNetosProg()    : 0;

                    // Las horas normales son hasta el máximo programado
                    int normales = Math.min(minTrab, minProg);
                    if (normales > 0) {
                        if (nocturno) minNormalesNoche += normales;
                        else          minNormalesDia   += normales;
                    }

                    // Horas extra = tiempos validados (previo + posterior)
                    int extra = (a.getValMinPrevIng()  != null ? a.getValMinPrevIng()  : 0)
                            + (a.getValMinPostSal()  != null ? a.getValMinPostSal()  : 0);

                    if (extra > 0) {
                        // Tasa A: hasta TOPE_TASA_A_MIN por día
                        int paraTasaA = Math.min(extra, TOPE_TASA_A_MIN);
                        int paraTasaB = Math.max(0, extra - TOPE_TASA_A_MIN);

                        if (nocturno) {
                            minExtraNocheA += paraTasaA;
                            minExtraNocheB += paraTasaB;
                        } else {
                            minExtraDiaA += paraTasaA;
                            minExtraDiaB += paraTasaB;
                        }
                    }

                    // Informativos
                    if (a.getMinTardanza()     != null) minTardanza   += a.getMinTardanza();
                    if (a.getMinSalTemprana()  != null) minSalTemprana+= a.getMinSalTemprana();
                }
            }
        }

        // Tasas vigentes (por defecto normativa peruana)
        BigDecimal tasaA = BigDecimal.valueOf(25.00);
        BigDecimal tasaB = BigDecimal.valueOf(35.00);

        // Si ya existía, preservar los campos manuales y tasas
        ConsolidadoQuincena.ConsolidadoQuincenaBuilder builder = ConsolidadoQuincena.builder()
                .quincena(quincena)
                .trabajador(trabajador)
                .minNormalesDia(minNormalesDia)
                .minNormalesNoche(minNormalesNoche)
                .tasaA(tasaA)
                .minExtraDiaA(minExtraDiaA)
                .minExtranocheA(minExtraNocheA)
                .tasaB(tasaB)
                .minExtraDiaB(minExtraDiaB)
                .minExtraNocheB(minExtraNocheB)
                .minDiaDescontar(minDiaDesc)
                .minNocheDescontar(minNocheDesc)
                .minTotalTardanza(minTardanza)
                .minTotalSalTemprana(minSalTemprana)
                .diasFalta(diasFalta)
                .diasPermiso(diasPermiso)
                .bolsaEntrada(bolsaEntrada)
                .estado("BORRADOR");

        if (existente != null) {
            // Preservar campos manuales y de bolsa decididos previamente
            builder.id(existente.getId())
                    .otroBono(existente.getOtroBono())
                    .detalleOtroBono(existente.getDetalleOtroBono())
                    .observaciones(existente.getObservaciones())
                    .bolsaAcumulada(existente.getBolsaAcumulada())
                    .bolsaConsumida(existente.getBolsaConsumida())
                    .generadoPor(existente.getGeneradoPor())
                    .generadoEn(existente.getGeneradoEn());
        }

        ConsolidadoQuincena result = builder.build();
        result.recalcularBolsaSalida();
        return result;
    }

    // ════════════════════════════════════════════════════════════
    // CONSULTAS
    // ════════════════════════════════════════════════════════════
    @Override
    public List<ConsolidadoResponse> getConsolidado(Long idQuincena) {
        return consolidadoRepo
                .findByQuincena_IdQuincenaOrderByTrabajador_APaterno(idQuincena)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public ConsolidadoResponse getConsolidadoTrabajador(Long idQuincena, Long idTrabajador) {
        // Si es TRABAJADOR, forzar que solo vea su propio consolidado
        idTrabajador = resolverIdTrabajador(idTrabajador);

        return toResponse(consolidadoRepo
                .findByQuincena_IdQuincenaAndTrabajador_IdTrabajador(idQuincena, idTrabajador)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No hay consolidado para ese trabajador en esta quincena.")));
    }

    // ════════════════════════════════════════════════════════════
    // EDICIÓN MANUAL
    // ════════════════════════════════════════════════════════════
    @Override
    @Transactional
    public ConsolidadoResponse editar(Long idConsolidado, EditarConsolidadoRequest req) {
        ConsolidadoQuincena c = consolidadoRepo.findById(idConsolidado)
                .orElseThrow(() -> new ResourceNotFoundException("Consolidado no encontrado."));

        if ("CERRADO".equals(c.getEstado()))
            throw new BusinessException("No se puede editar un consolidado cerrado.");

        if (req.getOtroBono()        != null) c.setOtroBono(req.getOtroBono());
        if (req.getDetalleOtroBono() != null) c.setDetalleOtroBono(req.getDetalleOtroBono());
        if (req.getObservaciones()   != null) c.setObservaciones(req.getObservaciones());

        return toResponse(consolidadoRepo.save(c));
    }

    // ════════════════════════════════════════════════════════════
    // CIERRE DE QUINCENA
    // ════════════════════════════════════════════════════════════
    @Override
    @Transactional
    public CierreQuincenaResponse cerrarQuincena(CerrarQuincenaRequest req, String username) {
        Quincena q = buscarQuincena(req.getIdQuincena());

        if (q.getEstado() == EstadoQuincena.CERRADA)
            throw new BusinessException("La quincena ya está cerrada.");

        List<ConsolidadoQuincena> consolidados =
                consolidadoRepo.findByQuincena_IdQuincenaOrderByTrabajador_APaterno(req.getIdQuincena());

        if (consolidados.isEmpty())
            throw new BusinessException(
                    "No hay consolidados generados. Genera el consolidado antes de cerrar.");

        // Mapa de decisiones por trabajador
        Map<Long, DecisionExtraDTO> decisiones = new HashMap<>();
        if (req.getDecisiones() != null) {
            for (DecisionExtraDTO d : req.getDecisiones())
                decisiones.put(d.getIdTrabajador(), d);
        }

        Long cerradoPor = usuarioRepo.findByUsername(username)
                .map(u -> u.getIdUsuario() != null ? u.getIdUsuario().longValue() : null)
                .orElse(null);

        int cerrados = 0;

        for (ConsolidadoQuincena c : consolidados) {
            if ("CERRADO".equals(c.getEstado())) { cerrados++; continue; }

            DecisionExtraDTO dec = decisiones.get(c.getTrabajador().getIdTrabajador());

            if (dec != null) {
                c.setMinExtraPagados(dec.getMinExtraPagados() != null ? dec.getMinExtraPagados() : 0);
                c.setMinExtraABolsa (dec.getMinExtraABolsa()  != null ? dec.getMinExtraABolsa()  : 0);
                c.setBolsaConsumida (dec.getBolsaConsumida()  != null ? dec.getBolsaConsumida()  : 0);
            } else {
                // Default: pagar todos los extras
                c.setMinExtraPagados(c.getTotalExtraMinutos());
                c.setMinExtraABolsa(0);
            }

            c.recalcularBolsaSalida();
            c.setEstado("CERRADO");
            c.setCerradoEn(LocalDateTime.now());
            c.setCerradoPor(cerradoPor);
            consolidadoRepo.save(c);
            cerrados++;
        }

        // Cerrar la quincena
        q.setEstado(EstadoQuincena.CERRADA);
        q.setCerradoPor(cerradoPor);
        q.setCerradoEn(LocalDateTime.now());
        quincenaRepo.save(q);

        auditoriaService.registrar("quincenas", req.getIdQuincena(), "CERRAR");

        return CierreQuincenaResponse.builder()
                .idQuincena(q.getIdQuincena())
                .descripcion(q.getDescripcion())
                .totalTrabajadores(consolidados.size())
                .consolidadosCerrados(cerrados)
                .cerradoEn(LocalDateTime.now().toString())
                .build();
    }

    // ════════════════════════════════════════════════════════════
    // REAPERTURA
    // ════════════════════════════════════════════════════════════
    @Override
    @Transactional
    public void solicitarReapertura(ReaperturaRequest req) {
        Quincena q = buscarQuincena(req.getIdQuincena());

        if (q.getEstado() != EstadoQuincena.CERRADA)
            throw new BusinessException("Solo se puede solicitar reapertura de quincenas cerradas.");

        q.setEstado(EstadoQuincena.REAPERTURA_PENDIENTE);
        q.setMotivoReapertura(req.getMotivo());
        quincenaRepo.save(q);

        auditoriaService.registrarCampo("quincenas", req.getIdQuincena(),
                "SOLICITAR_REAPER", "estado", "CERRADA", "REAPERTURA_PENDIENTE");
    }

    @Override
    @Transactional
    public void aprobarReapertura(Long idQuincena, String username) {
        Quincena q = buscarQuincena(idQuincena);

        if (q.getEstado() != EstadoQuincena.REAPERTURA_PENDIENTE)
            throw new BusinessException("La quincena no está en estado REAPERTURA_PENDIENTE.");

        Long reabiertoPor = usuarioRepo.findByUsername(username)
                .map(u -> u.getIdUsuario() != null ? u.getIdUsuario().longValue() : null)
                .orElse(null);

        q.setEstado(EstadoQuincena.ABIERTA);
        q.setReabiertoPor(reabiertoPor);
        q.setReabiertaEn(LocalDateTime.now());
        quincenaRepo.save(q);

        // Revertir consolidados a BORRADOR
        consolidadoRepo.findByQuincena_IdQuincenaOrderByTrabajador_APaterno(idQuincena)
                .forEach(c -> {
                    if ("CERRADO".equals(c.getEstado())) {
                        c.setEstado("BORRADOR");
                        c.setCerradoEn(null);
                        c.setCerradoPor(null);
                        consolidadoRepo.save(c);
                    }
                });

        auditoriaService.registrarCampo("quincenas", idQuincena,
                "APROBAR_REAPER", "estado", "REAPERTURA_PENDIENTE", "ABIERTA");
    }

    // ════════════════════════════════════════════════════════════
    // RESUMEN DE QUINCENAS
    // ════════════════════════════════════════════════════════════
    @Override
    public List<QuincenaConsolidadoResumenDTO> getQuincenasConResumen() {
        return quincenaRepo.findAll().stream()
                .sorted(Comparator
                        .comparingInt(Quincena::getAnio).reversed()
                        .thenComparingInt(Quincena::getMes).reversed()
                        .thenComparingInt(Quincena::getNumero).reversed())
                .map(q -> {
                    long totalC = consolidadoRepo.countByQuincena_IdQuincena(q.getIdQuincena());
                    long pend   = asistenciaRepo.countByQuincena_IdQuincenaAndEstadoIn(
                            q.getIdQuincena(),
                            List.of(EstadoAsistencia.CALCULADO, EstadoAsistencia.MARCADO,
                                    EstadoAsistencia.PENDIENTE));
                    return QuincenaConsolidadoResumenDTO.builder()
                            .idQuincena(q.getIdQuincena())
                            .descripcion(q.getDescripcion())
                            .fechaInicio(q.getFechaInicio().toString())
                            .fechaFin(q.getFechaFin().toString())
                            .estado(q.getEstado().name())
                            .totalConsolidados(totalC)
                            .pendientesRevision(pend)
                            .puedeGenerarse(pend == 0 && q.getEstado() != EstadoQuincena.CERRADA)
                            .puedeCerrarse(totalC > 0 && q.getEstado() == EstadoQuincena.ABIERTA)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════
    // HISTORIAL BOLSA
    // ════════════════════════════════════════════════════════════
    @Override
    public List<BolsaHistorialDTO> getHistorialBolsa(Long idTrabajador) {
        // Si es TRABAJADOR, forzar que solo vea su propio historial
        idTrabajador = resolverIdTrabajador(idTrabajador);

        return consolidadoRepo
                .findUltimosCerradosByTrabajador(idTrabajador, Long.MAX_VALUE)
                .stream()
                .map(c -> BolsaHistorialDTO.builder()
                        .idQuincena(c.getQuincena().getIdQuincena())
                        .quincenaDescripcion(c.getQuincena().getDescripcion())
                        .fechaInicio(c.getQuincena().getFechaInicio().toString())
                        .fechaFin(c.getQuincena().getFechaFin().toString())
                        .bolsaEntrada(c.getBolsaEntrada())
                        .bolsaAcumulada(c.getBolsaAcumulada())
                        .bolsaConsumida(c.getBolsaConsumida())
                        .bolsaSalida(c.getBolsaSalida())
                        .hBolsaEntrada(fmt(c.getBolsaEntrada()))
                        .hBolsaAcumulada(fmt(c.getBolsaAcumulada()))
                        .hBolsaConsumida(fmt(c.getBolsaConsumida()))
                        .hBolsaSalida(fmt(c.getBolsaSalida()))
                        .estadoQuincena(c.getQuincena().getEstado().name())
                        .minExtraPagados(c.getMinExtraPagados())
                        .minExtraABolsa(c.getMinExtraABolsa())
                        .build())
                .collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════
    // REPORTE COMPLETO DE QUINCENA
    // ════════════════════════════════════════════════════════════
    @Override
    public ConsolidadoReporteResponse getReporte(Long idQuincena) {
        Quincena q = buscarQuincena(idQuincena);
        List<ConsolidadoQuincena> lista =
                consolidadoRepo.findByQuincena_IdQuincenaOrderByTrabajador_APaterno(idQuincena);

        // Totales globales
        int totNormDia = 0, totNormNoche = 0;
        int totExtDia  = 0, totExtNoche  = 0;
        int totFaltas  = 0, totPermisos  = 0;

        for (ConsolidadoQuincena c : lista) {
            totNormDia   += c.getMinNormalesDia()   + c.getMinNormalesNoche(); // día
            totNormNoche += c.getMinNormalesNoche();
            totExtDia    += c.getMinExtraDiaA()  + c.getMinExtraDiaB();
            totExtNoche  += c.getMinExtranocheA() + c.getMinExtraNocheB();
            totFaltas    += c.getDiasFalta();
            totPermisos  += c.getDiasPermiso();
        }
        // recalcular totNormDia sin doble suma
        totNormDia = 0;
        for (ConsolidadoQuincena c : lista) totNormDia += c.getMinNormalesDia();

        int totGeneral = totNormDia + totNormNoche + totExtDia + totExtNoche;

        return ConsolidadoReporteResponse.builder()
                .idQuincena(q.getIdQuincena())
                .descripcion(q.getDescripcion())
                .fechaInicio(q.getFechaInicio().toString())
                .fechaFin(q.getFechaFin().toString())
                .estado(q.getEstado().name())
                .trabajadores(lista.stream().map(this::toResponse).collect(Collectors.toList()))
                .totalHNormalesDia(fmt(totNormDia))
                .totalHNormalesNoche(fmt(totNormNoche))
                .totalHExtraDia(fmt(totExtDia))
                .totalHExtraNoche(fmt(totExtNoche))
                .totalHGeneral(fmt(totGeneral))
                .totalDiasFalta(totFaltas)
                .totalDiasPermiso(totPermisos)
                .totalTrabajadores(lista.size())
                .build();
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════

    /**
     * Si el usuario autenticado es ROLE_TRABAJADOR, ignora el idTrabajador
     * recibido y devuelve el id del trabajador autenticado.
     * Para otros roles, devuelve el idTrabajador original sin cambios.
     */
    private Long resolverIdTrabajador(Long idTrabajador) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean esTrabajador = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TRABAJADOR"));

        if (esTrabajador) {
            String email = auth.getName();
            Usuario usuario = usuarioRepo.findByUsername(email)
                    .orElseThrow(() -> new BusinessException("Usuario no encontrado"));
            return usuario.getTrabajador().getIdTrabajador();
        }
        return idTrabajador;
    }

    private Quincena buscarQuincena(Long id) {
        return quincenaRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quincena no encontrada."));
    }

    private ConsolidadoResponse toResponse(ConsolidadoQuincena c) {
        Trabajador t = c.getTrabajador();
        int totalNorm  = c.getTotalNormalesMinutos();
        int totalExtra = c.getTotalExtraMinutos();

        return ConsolidadoResponse.builder()
                .id(c.getId())
                .idQuincena(c.getQuincena().getIdQuincena())
                .quincenaDescripcion(c.getQuincena().getDescripcion())
                .idTrabajador(t.getIdTrabajador())
                .trabajadorNombre(t.getPNombre() + " " + t.getAPaterno() + " " + t.getAMaterno())
                .puestoNombre(t.getPuesto() != null ? t.getPuesto().getPuesto() : null)
                .areaNombre(t.getPuesto() != null && t.getPuesto().getArea() != null
                        ? t.getPuesto().getArea().getArea() : null)
                .minNormalesDia(c.getMinNormalesDia())
                .minNormalesNoche(c.getMinNormalesNoche())
                .hNormalesDia(fmt(c.getMinNormalesDia()))
                .hNormalesNoche(fmt(c.getMinNormalesNoche()))
                .tasaA(c.getTasaA())
                .minExtraDiaA(c.getMinExtraDiaA())
                .minExtranocheA(c.getMinExtranocheA())
                .hExtraDiaA(fmt(c.getMinExtraDiaA()))
                .hExtraNocheA(fmt(c.getMinExtranocheA()))
                .tasaB(c.getTasaB())
                .minExtraDiaB(c.getMinExtraDiaB())
                .minExtraNocheB(c.getMinExtraNocheB())
                .hExtraDiaB(fmt(c.getMinExtraDiaB()))
                .hExtraNocheB(fmt(c.getMinExtraNocheB()))
                .hTotalNormales(fmt(totalNorm))
                .hTotalExtra(fmt(totalExtra))
                .hTotalGeneral(fmt(totalNorm + totalExtra))
                .minDiaDescontar(c.getMinDiaDescontar())
                .minNocheDescontar(c.getMinNocheDescontar())
                .minTotalTardanza(c.getMinTotalTardanza())
                .diasFalta(c.getDiasFalta())
                .diasPermiso(c.getDiasPermiso())
                .bolsaEntrada(c.getBolsaEntrada())
                .bolsaAcumulada(c.getBolsaAcumulada())
                .bolsaConsumida(c.getBolsaConsumida())
                .bolsaSalida(c.getBolsaSalida())
                .hBolsaEntrada(fmt(c.getBolsaEntrada()))
                .hBolsaSalida(fmt(c.getBolsaSalida()))
                .otroBono(c.getOtroBono())
                .detalleOtroBono(c.getDetalleOtroBono())
                .observaciones(c.getObservaciones())
                .minExtraPagados(c.getMinExtraPagados())
                .minExtraABolsa(c.getMinExtraABolsa())
                .estado(c.getEstado())
                .generadoEn(c.getGeneradoEn() != null ? c.getGeneradoEn().toString() : null)
                .cerradoEn(c.getCerradoEn()   != null ? c.getCerradoEn().toString()   : null)
                .build();
    }

    private String fmt(Integer min) {
        if (min == null || min == 0) return "00:00";
        int absMin = Math.abs(min);
        String sign = min < 0 ? "-" : "";
        return sign + String.format("%02d:%02d", absMin / 60, absMin % 60);
    }
}