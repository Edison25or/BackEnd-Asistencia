package com.idat.asistencia.controller;

import com.idat.asistencia.dto.GrupoTrabajoDTOs.*;
import com.idat.asistencia.service.GrupoTrabajoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/grupos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GrupoTrabajoController {

    private final GrupoTrabajoService service;

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','SUPERVISOR')")
    @GetMapping
    public ResponseEntity<List<GrupoResponse>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','SUPERVISOR')")
    @GetMapping("/{id}")
    public ResponseEntity<GrupoResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @PostMapping
    public ResponseEntity<GrupoResponse> crear(@Valid @RequestBody GrupoRequest request) {
        return new ResponseEntity<>(service.crear(request), HttpStatus.CREATED);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<GrupoResponse> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody GrupoRequest request) {
        return ResponseEntity.ok(service.actualizar(id, request));
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    // Remover un trabajador específico del grupo
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @DeleteMapping("/{idGrupo}/trabajadores/{idTrabajador}")
    public ResponseEntity<GrupoResponse> removerTrabajador(
            @PathVariable Integer idGrupo,
            @PathVariable Long idTrabajador) {
        return ResponseEntity.ok(service.removerTrabajador(idGrupo, idTrabajador));
    }
}