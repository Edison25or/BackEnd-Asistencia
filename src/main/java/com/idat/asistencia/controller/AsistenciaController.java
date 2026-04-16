package com.idat.asistencia.controller;

import com.idat.asistencia.dto.AsistenciaDTOs.*;
import com.idat.asistencia.service.AsistenciaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
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
public class AsistenciaController {

    private final AsistenciaService service;

    // ── Lector (público) ──────────────────────────────────────
    @PostMapping("/marcar")
    public ResponseEntity<MarcarAsistenciaResponse> marcar(@RequestParam String codigo) {
        return ResponseEntity.ok(service.marcar(codigo));
    }

    // ── Panel público para kiosco de marcado (sin autenticación) ─
    @GetMapping("/en-planta-publica")
    public ResponseEntity<List<EnPlantaPublicDTO>> enPlantaPublica() {
        return ResponseEntity.ok(service.getEnPlantaPublica());
    }

    // ── Panel del día (protegido — solo dashboard) ──────────
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE','SUPERVISOR')")
    @GetMapping("/en-planta")
    public ResponseEntity<List<AsistenciaResumenDTO>> enPlanta() {
        return ResponseEntity.ok(service.getTrabajadoresEnPlanta());
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE','SUPERVISOR')")
    @GetMapping("/dia")
    public ResponseEntity<List<AsistenciaResumenDTO>> dia() {
        return ResponseEntity.ok(service.getAsistenciasDia());
    }

    // ── Revisión de asistencias ───────────────────────────────
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE')")
    @GetMapping("/revision/{idQuincena}")
    public ResponseEntity<List<AsistenciaRevisionDTO>> revision(
            @PathVariable Long idQuincena) {
        return ResponseEntity.ok(service.getParaRevision(idQuincena));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE')")
    @PatchMapping("/validar-tiempos")
    public ResponseEntity<AsistenciaRevisionDTO> validar(
            @Valid @RequestBody ValidarTiemposRequest req,
            Authentication auth) {
        return ResponseEntity.ok(service.validarTiempos(req, auth.getName()));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE')")
    @PostMapping("/no-programada")
    public ResponseEntity<AsistenciaRevisionDTO> registrarNoProgramada(
            @Valid @RequestBody RegistrarNoProgramadaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.registrarNoProgramada(req));
    }

    // ── Quincenas ─────────────────────────────────────────────
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE','SUPERVISOR')")
    @GetMapping("/quincenas")
    public ResponseEntity<List<QuincenaResumenDTO>> quincenas() {
        return ResponseEntity.ok(service.getQuincenas());
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE')")
    @PostMapping("/quincenas")
    public ResponseEntity<QuincenaResumenDTO> crearQuincena(
            @Valid @RequestBody CrearQuincenaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.crearQuincena(req.getAnio(), req.getMes(), req.getNumero()));
    }

    @Data
    public static class CrearQuincenaRequest {
        @NotNull(message = "El año es obligatorio")
        @Min(value = 2020, message = "El año debe ser 2020 o posterior")
        @Max(value = 2100, message = "El año no puede ser mayor a 2100")
        private Integer anio;

        @NotNull(message = "El mes es obligatorio")
        @Min(value = 1, message = "El mes debe estar entre 1 y 12")
        @Max(value = 12, message = "El mes debe estar entre 1 y 12")
        private Integer mes;

        @NotNull(message = "El número de quincena es obligatorio")
        @Min(value = 1, message = "La quincena debe ser 1 (1-15) o 2 (16-fin)")
        @Max(value = 2, message = "La quincena debe ser 1 (1-15) o 2 (16-fin)")
        private Integer numero;
    }
}