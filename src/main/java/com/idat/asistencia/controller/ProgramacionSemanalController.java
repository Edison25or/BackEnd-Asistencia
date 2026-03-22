package com.idat.asistencia.controller;

import com.idat.asistencia.dto.ProgramacionDTOs.*;
import com.idat.asistencia.service.ProgramacionSemanalService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/programaciones")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProgramacionSemanalController {

    private final ProgramacionSemanalService service;

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','SUPERVISOR')")
    @GetMapping
    public ResponseEntity<List<ProgramacionResponse>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','SUPERVISOR','TRABAJADOR')")
    @GetMapping("/semana/{fecha}")
    public ResponseEntity<List<ProgramacionResponse>> getBySemana(@PathVariable String fecha) {
        return ResponseEntity.ok(service.getBySemana(fecha));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @PostMapping
    public ResponseEntity<ProgramacionResponse> crear(@Valid @RequestBody ProgramacionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @PostMapping("/desde-grupo")
    public ResponseEntity<ProgramacionBulkResponse> crearDesdeGrupo(@RequestBody BulkRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.crearDesdeGrupo(req.getIdGrupo(), req.getSemanaInicio(), req.getIdEsquema()));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @PostMapping("/confirmar-semana")
    public ResponseEntity<ConfirmarSemanaResponse> confirmarSemana(
            @RequestBody ConfirmarSemanaRequest req) {
        return ResponseEntity.ok(service.confirmarSemana(req.getSemanaInicio()));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @Data
    public static class ConfirmarSemanaRequest {
        private String semanaInicio;
    }

    @Data
    public static class BulkRequest {
        private Integer idGrupo;
        private String  semanaInicio;
        private Integer idEsquema;
    }
}