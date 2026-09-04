package com.idat.asistencia.controller;

import com.idat.asistencia.dto.ParametrosDTOs.*;
import com.idat.asistencia.service.ParametrosService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Configuracion global (CU26, CU27). */
@RestController
@RequestMapping("/api/parametros")
@RequiredArgsConstructor
public class ParametrosController {

    private final ParametrosService service;

    // Lectura abierta a los roles de gestion: el lector y la bandeja
    // necesitan conocer las tolerancias para explicar sus mensajes.
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE','SUPERVISOR')")
    @GetMapping("/generales")
    public ResponseEntity<ParametrosGeneralesResponse> verGenerales() {
        return ResponseEntity.ok(service.verGenerales());
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @PutMapping("/generales")
    public ResponseEntity<ParametrosGeneralesResponse> guardarGenerales(
            @Valid @RequestBody ParametrosGeneralesRequest req) {
        return ResponseEntity.ok(service.guardarGenerales(req));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @GetMapping("/quincena")
    public ResponseEntity<ParametrosQuincenaResponse> verQuincena() {
        return ResponseEntity.ok(service.verQuincena());
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @PutMapping("/quincena")
    public ResponseEntity<ParametrosQuincenaResponse> guardarQuincena(
            @Valid @RequestBody ParametrosQuincenaRequest req) {
        return ResponseEntity.ok(service.guardarQuincena(req));
    }
}
