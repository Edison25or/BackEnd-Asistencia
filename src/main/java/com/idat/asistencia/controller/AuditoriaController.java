package com.idat.asistencia.controller;

import com.idat.asistencia.model.entity.Auditoria;
import com.idat.asistencia.service.AuditoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/auditoria")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
public class AuditoriaController {

    private final AuditoriaService service;

    /**
     * Búsqueda paginada con filtros opcionales.
     * GET /api/auditoria?tabla=trabajadores&accion=MODIFICAR&page=0&size=20
     */
    @GetMapping
    public ResponseEntity<Page<Auditoria>> buscar(
            @RequestParam(required = false) String tabla,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) Long   idUsuario,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        LocalDateTime desdeTs = desde != null ? desde.atStartOfDay()        : null;
        LocalDateTime hastaTs = hasta != null ? hasta.atTime(23, 59, 59)    : null;

        Page<Auditoria> resultado = service.buscar(
                tabla, accion, idUsuario, desdeTs, hastaTs,
                PageRequest.of(page, size, Sort.by("fecha").descending())
        );
        return ResponseEntity.ok(resultado);
    }

    /**
     * Historial completo de un registro específico.
     * GET /api/auditoria/historial?tabla=trabajadores&idRegistro=10001
     */
    @GetMapping("/historial")
    public ResponseEntity<List<Auditoria>> historial(
            @RequestParam String tabla,
            @RequestParam Long   idRegistro
    ) {
        return ResponseEntity.ok(service.getHistorial(tabla, idRegistro));
    }
}