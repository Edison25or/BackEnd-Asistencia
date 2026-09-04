package com.idat.asistencia.service.impl;

import com.idat.asistencia.dto.ParametrosDTOs.*;
import com.idat.asistencia.exception.BusinessException;
import com.idat.asistencia.model.entity.ParametrosGeneralesAsistencia;
import com.idat.asistencia.model.entity.ParametrosQuincena;
import com.idat.asistencia.repository.ParametrosGeneralesAsistenciaRepository;
import com.idat.asistencia.repository.ParametrosQuincenaRepository;
import com.idat.asistencia.service.AuditoriaService;
import com.idat.asistencia.service.ParametrosService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParametrosServiceImpl implements ParametrosService {

    private final ParametrosGeneralesAsistenciaRepository generalesRepo;
    private final ParametrosQuincenaRepository            quincenaRepo;
    private final AuditoriaService                        auditoria;

    private static final String TABLA_GEN = "parametros_generales_asistencia";
    private static final String TABLA_QUI = "parametros_quincena";

    /**
     * Lee el registro unico. Si no existe lo crea con los valores por
     * defecto de la entidad, para que el sistema pueda operar desde el
     * primer arranque sin configuracion manual previa.
     */
    @Override
    @Transactional
    public ParametrosGeneralesAsistencia getGenerales() {
        return generalesRepo.findById(ParametrosGeneralesAsistencia.ID_UNICO)
                .orElseGet(() -> generalesRepo.save(
                        ParametrosGeneralesAsistencia.builder().build()));
    }

    @Override
    @Transactional
    public ParametrosQuincena getQuincena() {
        return quincenaRepo.findById(ParametrosQuincena.ID_UNICO)
                .orElseGet(() -> quincenaRepo.save(
                        ParametrosQuincena.builder().build()));
    }

    @Override
    public ParametrosGeneralesResponse verGenerales() {
        return toResponse(getGenerales());
    }

    @Override
    public ParametrosQuincenaResponse verQuincena() {
        return toResponse(getQuincena());
    }

    @Override
    @Transactional
    public ParametrosGeneralesResponse guardarGenerales(ParametrosGeneralesRequest req) {
        ParametrosGeneralesAsistencia p = getGenerales();

        String antes = resumen(p);

        p.setMaxAnticipacionEntrada(req.getMaxAnticipacionEntrada());
        p.setMaxExcesoSalida(req.getMaxExcesoSalida());
        p.setTopeCombinado(req.getTopeCombinado());
        if (req.getVentanaConfirmacionSeg() != null)
            p.setVentanaConfirmacionSeg(req.getVentanaConfirmacionSeg());
        if (req.getIntervaloAntirreboteSeg() != null)
            p.setIntervaloAntirreboteSeg(req.getIntervaloAntirreboteSeg());
        if (req.getDescontarRefrigerioFeriado() != null)
            p.setDescontarRefrigerioFeriado(req.getDescontarRefrigerioFeriado());

        // Validaciones cruzadas de P1, P2, P3 y de los tiempos del lector.
        // Viven en la entidad para que cualquier punto de escritura las
        // aplique, no solo este servicio (CU27).
        String error = p.validar();
        if (error != null) throw new BusinessException(error);

        ParametrosGeneralesAsistencia guardado = generalesRepo.save(p);

        auditoria.registrarCampo(TABLA_GEN, 1L, "MODIFICAR",
                "parametros", antes, resumen(guardado));

        return toResponse(guardado);
    }

    @Override
    @Transactional
    public ParametrosQuincenaResponse guardarQuincena(ParametrosQuincenaRequest req) {
        ParametrosQuincena p = getQuincena();

        String antes = p.getDiaCorteIntermedio() + " " + p.getHoraCorte();

        p.setDiaCorteIntermedio(req.getDiaCorteIntermedio());
        if (req.getHoraCorte() != null && !req.getHoraCorte().isBlank())
            p.setHoraCorte(LocalTime.parse(req.getHoraCorte()));

        String error = p.validar();
        if (error != null) throw new BusinessException(error);

        ParametrosQuincena guardado = quincenaRepo.save(p);

        // Los cambios aplican solo a quincenas futuras. Las ya generadas
        // conservan sus limites, porque estos viven en la propia Quincena
        // y no se recalculan (CU26, excepcion EX1).
        auditoria.registrarCampo(TABLA_QUI, 1L, "MODIFICAR", "corte",
                antes, guardado.getDiaCorteIntermedio() + " " + guardado.getHoraCorte());

        return toResponse(guardado);
    }

    // ---------- Helpers ----------

    private String resumen(ParametrosGeneralesAsistencia p) {
        return "P1=" + p.getMaxAnticipacionEntrada()
             + " P2=" + p.getMaxExcesoSalida()
             + " P3=" + p.getTopeCombinado()
             + " conf=" + p.getVentanaConfirmacionSeg() + "s"
             + " rebote=" + p.getIntervaloAntirreboteSeg() + "s";
    }

    private ParametrosGeneralesResponse toResponse(ParametrosGeneralesAsistencia p) {
        return ParametrosGeneralesResponse.builder()
                .maxAnticipacionEntrada(p.getMaxAnticipacionEntrada())
                .maxExcesoSalida(p.getMaxExcesoSalida())
                .topeCombinado(p.getTopeCombinado())
                .ventanaConfirmacionSeg(p.getVentanaConfirmacionSeg())
                .intervaloAntirreboteSeg(p.getIntervaloAntirreboteSeg())
                .descontarRefrigerioFeriado(p.isDescontarRefrigerioFeriado())
                .build();
    }

    private ParametrosQuincenaResponse toResponse(ParametrosQuincena p) {
        return ParametrosQuincenaResponse.builder()
                .diaCorteIntermedio(p.getDiaCorteIntermedio())
                .horaCorte(p.getHoraCorte().toString())
                .build();
    }
}
