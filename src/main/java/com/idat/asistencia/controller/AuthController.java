package com.idat.asistencia.controller;

import com.idat.asistencia.dto.AuthDTOs.AuthResponse;
import com.idat.asistencia.dto.AuthDTOs.LoginRequest;
import com.idat.asistencia.dto.AuthDTOs.RecuperarPasswordRequest;
import com.idat.asistencia.repository.UsuarioRepository;
import com.idat.asistencia.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService            jwtService;
    private final UsuarioRepository     usuarioRepository;
    private final PasswordEncoder       passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtService.generateToken(userDetails);
        return ResponseEntity.ok(new AuthResponse(token));
    }

    @PostMapping("/recuperar-password")
    public ResponseEntity<String> recuperarPassword(@RequestBody RecuperarPasswordRequest request) {
        // Mensaje genérico: devolvemos lo mismo existe o no el email.
        // Esto evita que atacantes puedan descubrir qué correos están registrados.
        String mensajeGenerico = "Si el correo está registrado, contacta al administrador del sistema " +
                "para que restablezca tu contraseña.";

        // Validación básica del email
        if (request.email() == null || request.email().isBlank()) {
            return ResponseEntity.ok(mensajeGenerico);
        }

        // No reseteamos automáticamente. El reset ahora es responsabilidad del SuperAdmin
        // mediante el endpoint /api/trabajadores/{id}/reset-password
        return ResponseEntity.ok(mensajeGenerico);
    }
}