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
public class TrabajadorController {

    private final TrabajadorService trabajadorService;

    // CREAR — Solo ADMIN y SUPERADMIN
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN')")
    @PostMapping
    public ResponseEntity<TrabajadorResponseDTO> createTrabajador(
            @Valid @RequestBody TrabajadorRequestDTO request) {
        return new ResponseEntity<>(trabajadorService.crearTrabajador(request), HttpStatus.CREATED);
    }

    // EDITAR — Todos los roles autenticados
    // (JEFE/SUPERVISOR/TRABAJADOR solo pueden editar su propio perfil; el servicio lo valida)
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN', 'JEFE', 'SUPERVISOR', 'TRABAJADOR')")
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

    // LISTAR — ADMIN, SUPERADMIN, JEFE y SUPERVISOR (estos últimos solo lectura)
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN', 'JEFE', 'SUPERVISOR')")
    @GetMapping
    public ResponseEntity<Page<TrabajadorResponseDTO>> getAllTrabajadores(
            @RequestParam(required = false, defaultValue = "ACTIVO") EstadoTrabajador estado,
            @PageableDefault(size = 20, sort = "idTrabajador") Pageable pageable) {
        return ResponseEntity.ok(trabajadorService.obtenerTodosLosTrabajadores(estado, pageable));
    }

    // VER DETALLE — Todos los roles autenticados
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN', 'JEFE', 'SUPERVISOR', 'TRABAJADOR')")
    @GetMapping("/{id}")
    public ResponseEntity<TrabajadorResponseDTO> getTrabajadorById(@PathVariable Long id) {
        return ResponseEntity.ok(trabajadorService.obtenerTrabajadorById(id));
    }

    // CESAR — Solo ADMIN y SUPERADMIN
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN')")
    @PatchMapping("/{id}/cesar")
    public ResponseEntity<Void> cesarTrabajador(
            @PathVariable Long id,
            @RequestParam(defaultValue = "Cese de actividades") String motivo,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fechaCese) {
        trabajadorService.cesarTrabajador(id, motivo, fechaCese != null ? fechaCese : java.time.LocalDate.now());
        return ResponseEntity.noContent().build();
    }

    // REINGRESAR — Solo ADMIN y SUPERADMIN
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN')")
    @PostMapping("/{id}/reingreso")
    public ResponseEntity<TrabajadorResponseDTO> reingresarTrabajador(
            @PathVariable Long id,
            // OPCIONAL (RN-12): sin idPuesto, el servicio conserva el
            // puesto del registro anterior, que es el caso habitual del
            // reingreso. Con required = true por defecto, omitirlo
            // devolvia 400 y obligaba a reelegir area y puesto aunque el
            // trabajador volviera al mismo sitio.
            @RequestParam(required = false) Integer idPuesto) {
        return ResponseEntity.ok(trabajadorService.reingresarTrabajador(id, idPuesto));
    }

    // BUSCAR — ADMIN, SUPERADMIN, JEFE, SUPERVISOR, TRABAJADOR (trabajador solo se ve a sí mismo)
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN', 'JEFE', 'SUPERVISOR', 'TRABAJADOR')")
    @GetMapping("/buscar")
    public ResponseEntity<Page<TrabajadorResponseDTO>> buscar(
            @RequestParam String q,
            @RequestParam(defaultValue = "ACTIVO") EstadoTrabajador estado,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(trabajadorService.buscarTrabajadores(q, estado, pageable));
    }

    // RESET PASSWORD — Solo SUPERADMIN
    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<String> resetearPassword(@PathVariable Long id) {
        trabajadorService.resetearPassword(id);
        return ResponseEntity.ok("Contraseña restablecida al número de documento.");
    }
}