package com.idat.asistencia.controller;

import com.idat.asistencia.dto.ConsolidadoDTOs.*;
import com.idat.asistencia.service.ConsolidadoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/consolidado")
@RequiredArgsConstructor
public class ConsolidadoController {

    private final ConsolidadoService service;

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE','SUPERVISOR','TRABAJADOR')")
    @GetMapping("/quincenas")
    public ResponseEntity<List<QuincenaConsolidadoResumenDTO>> quincenas() {
        return ResponseEntity.ok(service.getQuincenasConResumen());
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @PostMapping("/generar/{idQuincena}")
    public ResponseEntity<List<ConsolidadoResponse>> generar(@PathVariable Long idQuincena) {
        return ResponseEntity.ok(service.generarConsolidado(idQuincena));
    }
    /** Vista previa del consolidado, sin persistir ni cerrar (RN-36). */
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @GetMapping("/previsualizar/{idQuincena}")
    public ResponseEntity<List<ConsolidadoResponse>> previsualizar(@PathVariable Long idQuincena) {
        return ResponseEntity.ok(service.previsualizar(idQuincena));
    }


    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE','SUPERVISOR')")
    @GetMapping("/{idQuincena}")
    public ResponseEntity<List<ConsolidadoResponse>> listar(@PathVariable Long idQuincena) {
        return ResponseEntity.ok(service.getConsolidado(idQuincena));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE','SUPERVISOR','TRABAJADOR')")
    @GetMapping("/{idQuincena}/trabajador/{idTrabajador}")
    public ResponseEntity<ConsolidadoResponse> porTrabajador(
            @PathVariable Long idQuincena, @PathVariable Long idTrabajador) {
        return ResponseEntity.ok(service.getConsolidadoTrabajador(idQuincena, idTrabajador));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @PatchMapping("/{idConsolidado}")
    public ResponseEntity<ConsolidadoResponse> editar(
            @PathVariable Long idConsolidado,
            @Valid @RequestBody EditarConsolidadoRequest req) {
        return ResponseEntity.ok(service.editar(idConsolidado, req));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE','SUPERVISOR')")
    @GetMapping("/reporte/{idQuincena}")
    public ResponseEntity<ConsolidadoReporteResponse> reporte(@PathVariable Long idQuincena) {
        return ResponseEntity.ok(service.getReporte(idQuincena));
    }

    /**
     * Reapertura directa (RN-38). Reemplaza a los dos endpoints del
     * prototipo, solicitar-reaper y aprobar-reaper, que implementaban un
     * flujo de dos pasos no contemplado en el analisis.
     */
    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping("/reabrir")
    public ResponseEntity<Void> reabrir(@Valid @RequestBody ReaperturaRequest req) {
        service.reabrirQuincena(req);
        return ResponseEntity.ok().build();
    }
}
