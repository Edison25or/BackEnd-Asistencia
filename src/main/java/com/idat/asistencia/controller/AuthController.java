package com.idat.asistencia.controller;

import com.idat.asistencia.dto.AuthDTOs.AuthResponse;
import com.idat.asistencia.dto.AuthDTOs.LoginRequest;
import com.idat.asistencia.dto.AuthDTOs.RecuperarPasswordRequest;
import com.idat.asistencia.exception.ResourceNotFoundException;
import com.idat.asistencia.model.entity.Trabajador;
import com.idat.asistencia.model.entity.Usuario;
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
        Usuario usuario = usuarioRepository.findByUsername(request.email())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe una cuenta asociada a ese correo electrónico."));

        // La nueva contraseña será el nroDocumento del trabajador vinculado
        Trabajador trabajador = usuario.getTrabajador();
        if (trabajador == null) {
            throw new ResourceNotFoundException(
                    "No se encontró un trabajador vinculado a esta cuenta.");
        }

        String nuevaPassword = passwordEncoder.encode(trabajador.getNroDocumento());
        usuario.setPassword(nuevaPassword);
        usuarioRepository.save(usuario);

        return ResponseEntity.ok(
                "Contraseña restablecida. Tu nueva clave es tu número de documento (" +
                        trabajador.getNroDocumento() + ").");
    }
}