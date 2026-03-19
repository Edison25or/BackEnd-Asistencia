package com.idat.asistencia.controller;

import com.idat.asistencia.dto.AsistenciaDTOs.*;
import com.idat.asistencia.service.AsistenciaService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/asistencia")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AsistenciaController {

    private final AsistenciaService service;

    // ── Lector (público) ──────────────────────────────────────
    @PostMapping("/marcar")
    public ResponseEntity<MarcarAsistenciaResponse> marcar(@RequestParam String codigo) {
        return ResponseEntity.ok(service.marcar(codigo));
    }

    // ── Panel del día ─────────────────────────────────────────
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','SUPERVISOR')")
    @GetMapping("/en-planta")
    public ResponseEntity<List<AsistenciaResumenDTO>> enPlanta() {
        return ResponseEntity.ok(service.getTrabajadoresEnPlanta());
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','SUPERVISOR')")
    @GetMapping("/dia")
    public ResponseEntity<List<AsistenciaResumenDTO>> dia() {
        return ResponseEntity.ok(service.getAsistenciasDia());
    }

    // ── Revisión de asistencias ───────────────────────────────
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','SUPERVISOR')")
    @GetMapping("/revision/{idQuincena}")
    public ResponseEntity<List<AsistenciaRevisionDTO>> revision(
            @PathVariable Long idQuincena) {
        return ResponseEntity.ok(service.getParaRevision(idQuincena));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @PatchMapping("/validar-tiempos")
    public ResponseEntity<AsistenciaRevisionDTO> validar(
            @RequestBody ValidarTiemposRequest req,
            Authentication auth) {
        return ResponseEntity.ok(service.validarTiempos(req, auth.getName()));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @PostMapping("/no-programada")
    public ResponseEntity<AsistenciaRevisionDTO> registrarNoProgramada(
            @RequestBody RegistrarNoProgramadaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.registrarNoProgramada(req));
    }

    // ── Quincenas ─────────────────────────────────────────────
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','SUPERVISOR')")
    @GetMapping("/quincenas")
    public ResponseEntity<List<QuincenaResumenDTO>> quincenas() {
        return ResponseEntity.ok(service.getQuincenas());
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @PostMapping("/quincenas")
    public ResponseEntity<QuincenaResumenDTO> crearQuincena(
            @RequestBody CrearQuincenaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.crearQuincena(req.getAnio(), req.getMes(), req.getNumero()));
    }

    @Data
    public static class CrearQuincenaRequest {
        private Integer anio;
        private Integer mes;
        private Integer numero;
    }
}