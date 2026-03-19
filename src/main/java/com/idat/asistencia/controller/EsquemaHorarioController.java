package com.idat.asistencia.controller;

import com.idat.asistencia.dto.EsquemaHorarioDTOs.*;
import com.idat.asistencia.service.EsquemaHorarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/esquemas-horario")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EsquemaHorarioController {

    private final EsquemaHorarioService service;

    // ── Para dropdowns (programación, asistencia) ─────────────
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','SUPERVISOR')")
    @GetMapping
    public ResponseEntity<List<EsquemaResponse>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    // ── Para la pantalla de gestión (con historial de versiones) ─
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','SUPERVISOR')")
    @GetMapping("/agrupados")
    public ResponseEntity<List<EsquemaGrupoResponse>> getAllAgrupados() {
        return ResponseEntity.ok(service.getAllAgrupados());
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','SUPERVISOR')")
    @GetMapping("/{id}")
    public ResponseEntity<EsquemaResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.getById(id));
    }

    // ── Crear nuevo esquema (versión 1) ───────────────────────
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @PostMapping
    public ResponseEntity<EsquemaResponse> crear(@Valid @RequestBody EsquemaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request));
    }

    // ── Crear nueva versión de un esquema existente ───────────
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @PostMapping("/{grupoNombre}/nueva-version")
    public ResponseEntity<EsquemaResponse> crearNuevaVersion(
            @PathVariable String grupoNombre,
            @Valid @RequestBody NuevaVersionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.crearNuevaVersion(grupoNombre, request));
    }

    // ── Toggle activo/inactivo (reemplaza DELETE) ─────────────
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<EsquemaResponse> toggleActivo(@PathVariable Integer id) {
        return ResponseEntity.ok(service.toggleActivo(id));
    }

    // ── Contar programaciones (para modal de advertencia) ─────
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @GetMapping("/{id}/programaciones-count")
    public ResponseEntity<Long> contarProgramaciones(@PathVariable Integer id) {
        return ResponseEntity.ok(service.contarProgramaciones(id));
    }
}