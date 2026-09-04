package com.idat.asistencia.controller;

import com.idat.asistencia.dto.CatalogoDTOs.*;
import com.idat.asistencia.exception.BusinessException;
import com.idat.asistencia.exception.ResourceNotFoundException;
import com.idat.asistencia.model.entity.MotivoCese;
import com.idat.asistencia.model.entity.TipoAusencia;
import com.idat.asistencia.model.entity.Turno;
import com.idat.asistencia.repository.*;
import com.idat.asistencia.service.AuditoriaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Catalogos incorporados en esta version: Turno, Tipos de Ausencia y
 * Motivos de Cese (CU24).
 *
 * Se mantienen aparte de MaestroController, que sigue atendiendo Genero,
 * Area y Puesto sin cambios.
 */
@RestController
@RequestMapping("/api/catalogos")
@RequiredArgsConstructor
public class CatalogoController {

    private final TurnoRepository            turnoRepo;
    private final TipoAusenciaRepository     tipoAusenciaRepo;
    private final MotivoCeseRepository       motivoCeseRepo;
    private final EsquemaHorarioRepository   esquemaRepo;
    private final PermisoRepository          permisoRepo;
    private final FaltaJustificadaRepository faltaRepo;
    private final AuditoriaService           auditoria;

    // ════════════════════════════════════════════════════════════
    // TURNOS (RN-18)
    // ════════════════════════════════════════════════════════════

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE','SUPERVISOR')")
    @GetMapping("/turnos")
    public ResponseEntity<List<TurnoResponse>> turnos() {
        return ResponseEntity.ok(turnoRepo.findByActivoTrueOrderByNombreAsc()
                .stream().map(this::toResponse).collect(Collectors.toList()));
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping("/turnos")
    @Transactional
    public ResponseEntity<TurnoResponse> crearTurno(@Valid @RequestBody TurnoRequest req) {
        if (turnoRepo.existsByNombreIgnoreCase(req.getNombre()))
            throw new BusinessException("Ya existe un turno con ese nombre.");

        Turno t = turnoRepo.save(Turno.builder()
                .nombre(req.getNombre())
                .horaInicio(hora(req.getHoraInicio()))
                .horaFin(hora(req.getHoraFin()))
                .activo(true)
                .build());

        auditoria.registrar("turnos", t.getIdTurno().longValue(), "CREAR");
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(t));
    }

    /**
     * Un turno en uso por algun esquema no se desactiva: dejaria
     * jornadas sin clasificar en el consolidado (RN-18, RN-25).
     */
    @PreAuthorize("hasRole('SUPERADMIN')")
    @DeleteMapping("/turnos/{id}")
    @Transactional
    public ResponseEntity<Void> desactivarTurno(@PathVariable Integer id) {
        Turno t = turnoRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Turno no encontrado: " + id));

        long enUso = esquemaRepo.countByTurno_IdTurnoAndVigenteHastaIsNull(id);
        if (enUso > 0)
            throw new BusinessException("No se puede desactivar el turno '" + t.getNombre()
                    + "': lo usan " + enUso + " esquema(s) de horario vigentes.");

        t.setActivo(false);
        turnoRepo.save(t);
        auditoria.registrarCampo("turnos", id.longValue(),
                "DESHABILITAR", "activo", "true", "false");
        return ResponseEntity.noContent().build();
    }

    // ════════════════════════════════════════════════════════════
    // TIPOS DE AUSENCIA (RN-16)
    // ════════════════════════════════════════════════════════════

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE')")
    @GetMapping("/tipos-ausencia")
    public ResponseEntity<List<CatalogoSimpleResponse>> tiposAusencia() {
        return ResponseEntity.ok(tipoAusenciaRepo.findByActivoTrueOrderByNombreAsc()
                .stream()
                .map(t -> CatalogoSimpleResponse.builder()
                        .id(t.getIdTipoAusencia()).nombre(t.getNombre())
                        .descripcion(t.getDescripcion()).activo(t.isActivo()).build())
                .collect(Collectors.toList()));
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping("/tipos-ausencia")
    @Transactional
    public ResponseEntity<CatalogoSimpleResponse> crearTipoAusencia(
            @Valid @RequestBody CatalogoSimpleRequest req) {
        if (tipoAusenciaRepo.existsByNombreIgnoreCase(req.getNombre()))
            throw new BusinessException("Ya existe un tipo de ausencia con ese nombre.");

        TipoAusencia t = tipoAusenciaRepo.save(TipoAusencia.builder()
                .nombre(req.getNombre()).descripcion(req.getDescripcion())
                .activo(true).build());

        auditoria.registrar("tipos_ausencia", t.getIdTipoAusencia().longValue(), "CREAR");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CatalogoSimpleResponse.builder()
                        .id(t.getIdTipoAusencia()).nombre(t.getNombre())
                        .descripcion(t.getDescripcion()).activo(true).build());
    }

    /**
     * Se permite desactivar aunque tenga uso historico (RN-16): los
     * permisos y faltas ya registrados conservan su referencia.
     */
    @PreAuthorize("hasRole('SUPERADMIN')")
    @DeleteMapping("/tipos-ausencia/{id}")
    @Transactional
    public ResponseEntity<Void> desactivarTipoAusencia(@PathVariable Integer id) {
        TipoAusencia t = tipoAusenciaRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de ausencia no encontrado."));
        t.setActivo(false);
        tipoAusenciaRepo.save(t);
        auditoria.registrarCampo("tipos_ausencia", id.longValue(),
                "DESHABILITAR", "activo", "true", "false");
        return ResponseEntity.noContent().build();
    }

    // ════════════════════════════════════════════════════════════
    // MOTIVOS DE CESE (RN-11)
    // ════════════════════════════════════════════════════════════

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @GetMapping("/motivos-cese")
    public ResponseEntity<List<CatalogoSimpleResponse>> motivosCese() {
        return ResponseEntity.ok(motivoCeseRepo.findByActivoTrueOrderByNombreAsc()
                .stream()
                .map(m -> CatalogoSimpleResponse.builder()
                        .id(m.getIdMotivoCese()).nombre(m.getNombre())
                        .activo(m.isActivo()).build())
                .collect(Collectors.toList()));
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping("/motivos-cese")
    @Transactional
    public ResponseEntity<CatalogoSimpleResponse> crearMotivoCese(
            @Valid @RequestBody CatalogoSimpleRequest req) {
        if (motivoCeseRepo.existsByNombreIgnoreCase(req.getNombre()))
            throw new BusinessException("Ya existe un motivo de cese con ese nombre.");

        MotivoCese m = motivoCeseRepo.save(MotivoCese.builder()
                .nombre(req.getNombre()).activo(true).build());

        auditoria.registrar("motivos_cese", m.getIdMotivoCese().longValue(), "CREAR");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CatalogoSimpleResponse.builder()
                        .id(m.getIdMotivoCese()).nombre(m.getNombre()).activo(true).build());
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @DeleteMapping("/motivos-cese/{id}")
    @Transactional
    public ResponseEntity<Void> desactivarMotivoCese(@PathVariable Integer id) {
        MotivoCese m = motivoCeseRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Motivo de cese no encontrado."));
        m.setActivo(false);
        motivoCeseRepo.save(m);
        auditoria.registrarCampo("motivos_cese", id.longValue(),
                "DESHABILITAR", "activo", "true", "false");
        return ResponseEntity.noContent().build();
    }

    // ---------- Helpers ----------

    private TurnoResponse toResponse(Turno t) {
        return TurnoResponse.builder()
                .idTurno(t.getIdTurno())
                .nombre(t.getNombre())
                .horaInicio(t.getHoraInicio() != null ? t.getHoraInicio().toString() : null)
                .horaFin(t.getHoraFin() != null ? t.getHoraFin().toString() : null)
                .cruzaMedianoche(t.isCruzaMedianoche())
                .activo(t.isActivo())
                .build();
    }

    private LocalTime hora(String s) {
        return (s == null || s.isBlank()) ? null : LocalTime.parse(s);
    }
}
