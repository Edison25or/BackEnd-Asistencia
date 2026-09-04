package com.idat.asistencia.controller;

import com.idat.asistencia.dto.FeriadoDTOs.*;
import com.idat.asistencia.service.FeriadoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Catalogo de feriados y computo de horas en ellos (CU24, RN-41). */
@RestController
@RequestMapping("/api/feriados")
@RequiredArgsConstructor
public class FeriadoController {

    private final FeriadoService service;

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE','SUPERVISOR','TRABAJADOR')")
    @GetMapping
    public ResponseEntity<List<FeriadoResponse>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    /**
     * Conteo de registros afectados ANTES de confirmar.
     *
     * Con dos turnos en paralelo, a que jornadas alcanza un feriado no es
     * evidente: la nocturna de la vispera aporta minutos aunque su fecha
     * sea el dia anterior.
     */
    @PreAuthorize("hasRole('SUPERADMIN')")
    @GetMapping("/previsualizar")
    public ResponseEntity<ImpactoFeriadoResponse> previsualizar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(service.previsualizar(fecha));
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping
    public ResponseEntity<ImpactoFeriadoResponse> registrar(
            @Valid @RequestBody FeriadoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registrar(req));
    }

    /** Revierte el computo y la marca de no laborable. */
    @PreAuthorize("hasRole('SUPERADMIN')")
    @DeleteMapping("/{idFeriado}")
    public ResponseEntity<Void> desactivar(@PathVariable Integer idFeriado) {
        service.desactivar(idFeriado);
        return ResponseEntity.noContent().build();
    }
}
