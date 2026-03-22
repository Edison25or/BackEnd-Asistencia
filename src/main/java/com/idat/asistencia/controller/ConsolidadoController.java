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

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','SUPERVISOR','TRABAJADOR')")
    @GetMapping("/quincenas")
    public ResponseEntity<List<QuincenaConsolidadoResumenDTO>> quincenas() {
        return ResponseEntity.ok(service.getQuincenasConResumen());
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @PostMapping("/generar/{idQuincena}")
    public ResponseEntity<List<ConsolidadoResponse>> generar(
            @PathVariable Long idQuincena) {
        return ResponseEntity.ok(service.generarConsolidado(idQuincena));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','SUPERVISOR')")
    @GetMapping("/{idQuincena}")
    public ResponseEntity<List<ConsolidadoResponse>> listar(
            @PathVariable Long idQuincena) {
        return ResponseEntity.ok(service.getConsolidado(idQuincena));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','SUPERVISOR','TRABAJADOR')")
    @GetMapping("/{idQuincena}/trabajador/{idTrabajador}")
    public ResponseEntity<ConsolidadoResponse> porTrabajador(
            @PathVariable Long idQuincena,
            @PathVariable Long idTrabajador) {
        return ResponseEntity.ok(service.getConsolidadoTrabajador(idQuincena, idTrabajador));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @PatchMapping("/{idConsolidado}")
    public ResponseEntity<ConsolidadoResponse> editar(
            @PathVariable Long idConsolidado,
            @RequestBody EditarConsolidadoRequest req) {
        return ResponseEntity.ok(service.editar(idConsolidado, req));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @PostMapping("/cerrar")
    public ResponseEntity<CierreQuincenaResponse> cerrar(
            @RequestBody CerrarQuincenaRequest req,
            Authentication auth) {
        return ResponseEntity.ok(service.cerrarQuincena(req, auth.getName()));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @PostMapping("/solicitar-reaper")
    public ResponseEntity<Void> solicitarReapertura(@RequestBody ReaperturaRequest req) {
        service.solicitarReapertura(req);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','SUPERVISOR','TRABAJADOR')")
    @GetMapping("/bolsa/{idTrabajador}")
    public ResponseEntity<List<BolsaHistorialDTO>> historialBolsa(
            @PathVariable Long idTrabajador) {
        return ResponseEntity.ok(service.getHistorialBolsa(idTrabajador));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','SUPERVISOR')")
    @GetMapping("/reporte/{idQuincena}")
    public ResponseEntity<ConsolidadoReporteResponse> reporte(
            @PathVariable Long idQuincena) {
        return ResponseEntity.ok(service.getReporte(idQuincena));
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping("/{idQuincena}/aprobar-reaper")
    public ResponseEntity<Void> aprobarReapertura(
            @PathVariable Long idQuincena,
            Authentication auth) {
        service.aprobarReapertura(idQuincena, auth.getName());
        return ResponseEntity.ok().build();
    }
}