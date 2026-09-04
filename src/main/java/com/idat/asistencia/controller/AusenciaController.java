package com.idat.asistencia.controller;

import com.idat.asistencia.dto.AusenciaDTOs.*;
import com.idat.asistencia.service.AusenciaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Permisos y faltas justificadas (CU16, CU17). */
@RestController
@RequestMapping("/api/ausencias")
@RequiredArgsConstructor
public class AusenciaController {

    private final AusenciaService service;

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE')")
    @PostMapping("/permisos")
    public ResponseEntity<PermisoResponse> registrarPermiso(
            @Valid @RequestBody PermisoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.registrarPermiso(req));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE')")
    @PostMapping("/faltas-justificadas")
    public ResponseEntity<FaltaJustificadaResponse> registrarFalta(
            @Valid @RequestBody FaltaJustificadaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.registrarFaltaJustificada(req));
    }

    /**
     * Elimina un permiso y revierte su efecto (RN-44).
     *
     * No hay endpoint de edicion: cambiar el rango obligaria a revertir y
     * volver a neutralizar en una sola operacion, y cualquier fallo
     * intermedio dejaria pre-registros a medio camino. Eliminar y volver
     * a crear es equivalente y no puede quedar a medias.
     *
     * @return numero de pre-registros que volvieron a pendiente
     */
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE')")
    @DeleteMapping("/permisos/{idPermiso}")
    public ResponseEntity<Integer> eliminarPermiso(@PathVariable Long idPermiso) {
        return ResponseEntity.ok(service.eliminarPermiso(idPermiso));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE')")
    @DeleteMapping("/faltas-justificadas/{idFalta}")
    public ResponseEntity<Integer> eliminarFalta(@PathVariable Long idFalta) {
        return ResponseEntity.ok(service.eliminarFaltaJustificada(idFalta));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE','SUPERVISOR','TRABAJADOR')")
    @GetMapping("/permisos/{idTrabajador}")
    public ResponseEntity<List<PermisoResponse>> permisos(
            @PathVariable Long idTrabajador,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(service.listarPermisos(idTrabajador, desde, hasta));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE','SUPERVISOR','TRABAJADOR')")
    @GetMapping("/faltas-justificadas/{idTrabajador}")
    public ResponseEntity<List<FaltaJustificadaResponse>> faltas(
            @PathVariable Long idTrabajador,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(service.listarFaltasJustificadas(idTrabajador, desde, hasta));
    }
}
