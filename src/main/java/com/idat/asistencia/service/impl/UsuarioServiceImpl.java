package com.idat.asistencia.service.impl;

import com.idat.asistencia.dto.UsuarioInfoDTO;
import com.idat.asistencia.exception.BusinessException;
import com.idat.asistencia.exception.ResourceNotFoundException;
import com.idat.asistencia.model.entity.Trabajador;
import com.idat.asistencia.model.entity.Usuario;
import com.idat.asistencia.repository.UsuarioRepository;
import com.idat.asistencia.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder   passwordEncoder;

    @Override
    public String cambiarRol(Integer idTrabajador, String nuevoRol) {
        List<String> rolesValidos = List.of(
                "ROLE_SUPERADMIN", "ROLE_ADMIN", "ROLE_JEFE", "ROLE_SUPERVISOR", "ROLE_TRABAJADOR"
        );
        if (!rolesValidos.contains(nuevoRol))
            throw new IllegalArgumentException("El rol ingresado no es válido.");

        Usuario usuario = usuarioRepository.findByTrabajador_IdTrabajador(idTrabajador)
                .orElseThrow(() -> new RuntimeException("No se encontró un usuario para este trabajador"));

        usuario.setRol(nuevoRol);
        usuarioRepository.save(usuario);
        return "Rol actualizado exitosamente a " + nuevoRol;
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioInfoDTO getMiInfo(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + username));

        String nombre;
        boolean debeCambiarPassword = false;

        if (usuario.getTrabajador() != null) {
            Trabajador t = usuario.getTrabajador();
            nombre = t.getPNombre()
                    + (t.getSNombre() != null ? " " + t.getSNombre() : "")
                    + " " + t.getAPaterno()
                    + " " + t.getAMaterno();

            // Detectar si la contraseña sigue siendo el DNI (contraseña temporal)
            debeCambiarPassword = passwordEncoder.matches(t.getNroDocumento(), usuario.getPassword());
        } else {
            nombre = usuario.getUsername();
        }

        return UsuarioInfoDTO.builder()
                .nombre(nombre.trim())
                .rol(usuario.getRol())
                .email(usuario.getUsername())
                .debeCambiarPassword(debeCambiarPassword)
                // Añades la línea aquí:
                .idTrabajador(usuario.getTrabajador() != null ? usuario.getTrabajador().getIdTrabajador() : null)
                .build();
    }


    // Punto 5: regex de contraseña segura
    private static final String PASSWORD_REGEX =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()\\-_=+\\[\\]{}|;:,.<>?])[^\\s]{10,20}$";

    @Override
    @Transactional
    public void cambiarPassword(String username, String passwordActual, String passwordNueva) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        if (!passwordEncoder.matches(passwordActual, usuario.getPassword()))
            throw new BusinessException("La contraseña actual es incorrecta.");

        if (passwordEncoder.matches(passwordNueva, usuario.getPassword()))
            throw new BusinessException("La nueva contraseña debe ser diferente a la actual.");

        if (!passwordNueva.matches(PASSWORD_REGEX))
            throw new BusinessException(
                    "La contraseña debe tener entre 10 y 20 caracteres, incluir al menos " +
                            "una mayúscula, una minúscula, un número, un carácter especial y no contener espacios."
            );

        usuario.setPassword(passwordEncoder.encode(passwordNueva));
        usuarioRepository.save(usuario);
    }
}