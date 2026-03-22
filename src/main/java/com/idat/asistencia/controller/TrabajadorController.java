package com.idat.asistencia.controller;

import com.idat.asistencia.dto.TrabajadorRequestDTO;
import com.idat.asistencia.dto.TrabajadorResponseDTO;
import com.idat.asistencia.model.enums.EstadoTrabajador;
import com.idat.asistencia.service.TrabajadorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trabajadores")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TrabajadorController {

    private final TrabajadorService trabajadorService;

    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN')")
    @PostMapping
    public ResponseEntity<TrabajadorResponseDTO> createTrabajador(
            @Valid @RequestBody TrabajadorRequestDTO request) {
        return new ResponseEntity<>(trabajadorService.crearTrabajador(request), HttpStatus.CREATED);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN', 'SUPERVISOR', 'TRABAJADOR')")
    @PutMapping("/{id}")
    public ResponseEntity<TrabajadorResponseDTO> updateTrabajador(
            @PathVariable Long id,
            @Valid @RequestBody TrabajadorRequestDTO request,
            Authentication authentication) {

        String rol = authentication.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority())
                .orElse("");

        return ResponseEntity.ok(trabajadorService.actualizarTrabajador(id, request, rol));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN', 'SUPERVISOR')")
    @GetMapping
    public ResponseEntity<Page<TrabajadorResponseDTO>> getAllTrabajadores(
            @RequestParam(required = false, defaultValue = "ACTIVO") EstadoTrabajador estado,
            @PageableDefault(size = 20, sort = "idTrabajador") Pageable pageable) {
        return ResponseEntity.ok(trabajadorService.obtenerTodosLosTrabajadores(estado, pageable));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN', 'SUPERVISOR', 'TRABAJADOR')")
    @GetMapping("/{id}")
    public ResponseEntity<TrabajadorResponseDTO> getTrabajadorById(@PathVariable Long id) {
        return ResponseEntity.ok(trabajadorService.obtenerTrabajadorById(id));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN')")
    @PatchMapping("/{id}/cesar")
    public ResponseEntity<Void> cesarTrabajador(
            @PathVariable Long id,
            @RequestParam(defaultValue = "Cese de actividades") String motivo) {
        trabajadorService.cesarTrabajador(id, motivo);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN')")
    @PostMapping("/{id}/reingreso")
    public ResponseEntity<TrabajadorResponseDTO> reingresarTrabajador(
            @PathVariable Long id,
            @RequestParam Integer idPuesto) {
        return ResponseEntity.ok(trabajadorService.reingresarTrabajador(id, idPuesto));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN', 'SUPERVISOR', 'TRABAJADOR')")
    @GetMapping("/buscar")
    public ResponseEntity<Page<TrabajadorResponseDTO>> buscar(
            @RequestParam String q,
            @RequestParam(defaultValue = "ACTIVO") EstadoTrabajador estado,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(trabajadorService.buscarTrabajadores(q, estado, pageable));
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<String> resetearPassword(@PathVariable Long id) {
        trabajadorService.resetearPassword(id);
        return ResponseEntity.ok("Contraseña restablecida al número de documento.");
    }
}