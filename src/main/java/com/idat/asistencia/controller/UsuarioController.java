package com.idat.asistencia.controller;

import com.idat.asistencia.dto.AuthDTOs.CambiarPasswordRequest;
import com.idat.asistencia.dto.UsuarioInfoDTO;
import com.idat.asistencia.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;


    @GetMapping("/me")
    public ResponseEntity<UsuarioInfoDTO> getMiInfo(Authentication authentication) {
        return ResponseEntity.ok(usuarioService.getMiInfo(authentication.getName()));
    }

    @PutMapping("/me/password")
    public ResponseEntity<String> cambiarPassword(
            Authentication authentication,
            @RequestBody CambiarPasswordRequest request) {

        usuarioService.cambiarPassword(
                authentication.getName(),
                request.passwordActual(),
                request.passwordNueva()
        );
        return ResponseEntity.ok("Contraseña actualizada correctamente.");
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @PutMapping("/trabajador/{idTrabajador}/rol")
    public ResponseEntity<String> cambiarRol(
            @PathVariable Integer idTrabajador,
            @RequestBody Map<String, String> request) {

        String nuevoRol = request.get("rol");
        return ResponseEntity.ok(usuarioService.cambiarRol(idTrabajador, nuevoRol));
    }
}