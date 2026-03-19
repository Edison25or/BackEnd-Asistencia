package com.idat.asistencia.controller;

import com.idat.asistencia.dto.ConsolidadoDTOs.*;
import com.idat.asistencia.service.ConsolidadoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.idat.asistencia.dto.ConsolidadoDTOs.BolsaHistorialDTO;
import com.idat.asistencia.dto.ConsolidadoDTOs.ConsolidadoReporteResponse;
import java.util.List;

@RestController
@RequestMapping("/api/consolidado")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ConsolidadoController {

    private final ConsolidadoService service;

    // ── Resumen de quincenas ──────────────────────────────────
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','SUPERVISOR')")
    @GetMapping("/quincenas")
    public ResponseEntity<List<QuincenaConsolidadoResumenDTO>> quincenas() {
        return ResponseEntity.ok(service.getQuincenasConResumen());
    }

    // ── Generar consolidado de una quincena ───────────────────
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @PostMapping("/generar/{idQuincena}")
    public ResponseEntity<List<ConsolidadoResponse>> generar(
            @PathVariable Long idQuincena) {
        return ResponseEntity.ok(service.generarConsolidado(idQuincena));
    }

    // ── Listar consolidado de una quincena ────────────────────
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','SUPERVISOR')")
    @GetMapping("/{idQuincena}")
    public ResponseEntity<List<ConsolidadoResponse>> listar(
            @PathVariable Long idQuincena) {
        return ResponseEntity.ok(service.getConsolidado(idQuincena));
    }

    // ── Consolidado de un trabajador específico ───────────────
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','SUPERVISOR')")
    @GetMapping("/{idQuincena}/trabajador/{idTrabajador}")
    public ResponseEntity<ConsolidadoResponse> porTrabajador(
            @PathVariable Long idQuincena,
            @PathVariable Long idTrabajador) {
        return ResponseEntity.ok(service.getConsolidadoTrabajador(idQuincena, idTrabajador));
    }

    // ── Editar campos manuales ────────────────────────────────
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @PatchMapping("/{idConsolidado}")
    public ResponseEntity<ConsolidadoResponse> editar(
            @PathVariable Long idConsolidado,
            @RequestBody EditarConsolidadoRequest req) {
        return ResponseEntity.ok(service.editar(idConsolidado, req));
    }

    // ── Cerrar quincena ───────────────────────────────────────
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @PostMapping("/cerrar")
    public ResponseEntity<CierreQuincenaResponse> cerrar(
            @RequestBody CerrarQuincenaRequest req,
            Authentication auth) {
        return ResponseEntity.ok(service.cerrarQuincena(req, auth.getName()));
    }

    // ── Solicitar reapertura (Admin) ──────────────────────────
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @PostMapping("/solicitar-reaper")
    public ResponseEntity<Void> solicitarReapertura(@RequestBody ReaperturaRequest req) {
        service.solicitarReapertura(req);
        return ResponseEntity.ok().build();
    }

    // ── Historial de bolsa de un trabajador ──────────────────
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','SUPERVISOR')")
    @GetMapping("/bolsa/{idTrabajador}")
    public ResponseEntity<List<BolsaHistorialDTO>> historialBolsa(
            @PathVariable Long idTrabajador) {
        return ResponseEntity.ok(service.getHistorialBolsa(idTrabajador));
    }

    // ── Reporte consolidado exportable (todos los trabajadores) ─
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','SUPERVISOR')")
    @GetMapping("/reporte/{idQuincena}")
    public ResponseEntity<ConsolidadoReporteResponse> reporte(
            @PathVariable Long idQuincena) {
        return ResponseEntity.ok(service.getReporte(idQuincena));
    }

    // ── Aprobar reapertura (SuperAdmin) ───────────────────────
    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping("/{idQuincena}/aprobar-reaper")
    public ResponseEntity<Void> aprobarReapertura(
            @PathVariable Long idQuincena,
            Authentication auth) {
        service.aprobarReapertura(idQuincena, auth.getName());
        return ResponseEntity.ok().build();
    }
}