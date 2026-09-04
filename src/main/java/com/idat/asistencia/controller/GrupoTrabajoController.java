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
public class GrupoTrabajoController {

    private final GrupoTrabajoService service;

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE','SUPERVISOR')")
    @GetMapping
    public ResponseEntity<List<GrupoResponse>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE','SUPERVISOR')")
    @GetMapping("/{id}")
    public ResponseEntity<GrupoResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.getById(id));
    }

    /**
     * Candidatos para armar un grupo: activos del area y sin grupo
     * asignado (RN-20, RN-21). Evita que la interfaz ofrezca a alguien
     * que el servidor va a rechazar.
     */
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE')")
    @GetMapping("/disponibles/{idArea}")
    public ResponseEntity<List<TrabajadorResumenDTO>> disponibles(@PathVariable Integer idArea) {
        return ResponseEntity.ok(service.getDisponibles(idArea));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE')")
    @PostMapping
    public ResponseEntity<GrupoResponse> crear(@Valid @RequestBody GrupoRequest request) {
        return new ResponseEntity<>(service.crear(request), HttpStatus.CREATED);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE')")
    @PutMapping("/{id}")
    public ResponseEntity<GrupoResponse> actualizar(
            @PathVariable Integer id, @Valid @RequestBody GrupoRequest request) {
        return ResponseEntity.ok(service.actualizar(id, request));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE')")
    @PostMapping("/{idGrupo}/trabajadores")
    public ResponseEntity<GrupoResponse> asignar(
            @PathVariable Integer idGrupo, @RequestBody List<Long> idsTrabajadores) {
        return ResponseEntity.ok(service.asignarTrabajadores(idGrupo, idsTrabajadores));
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE')")
    @DeleteMapping("/{idGrupo}/trabajadores/{idTrabajador}")
    public ResponseEntity<GrupoResponse> removerTrabajador(
            @PathVariable Integer idGrupo, @PathVariable Long idTrabajador) {
        return ResponseEntity.ok(service.removerTrabajador(idGrupo, idTrabajador));
    }
}
