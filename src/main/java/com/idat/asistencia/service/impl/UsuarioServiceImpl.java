package com.idat.asistencia.service.impl;

import com.idat.asistencia.dto.UsuarioInfoDTO;
import com.idat.asistencia.exception.BusinessException;
import com.idat.asistencia.exception.ResourceNotFoundException;
import com.idat.asistencia.model.entity.Trabajador;
import com.idat.asistencia.model.entity.Usuario;
import com.idat.asistencia.repository.TrabajadorRepository;
import com.idat.asistencia.repository.UsuarioRepository;
import com.idat.asistencia.service.AuditoriaService;
import com.idat.asistencia.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Gestion de usuarios, roles y contrasenas (CU01, CU02, CU25).
 *
 * ============================================================
 * CAMBIO PRINCIPAL
 * ============================================================
 * La condicion de cambio obligatorio deja de inferirse comparando el hash
 * de la contrasena contra el numero de documento, y pasa a leerse del
 * campo persistido Usuario.debeCambiarPassword (RN-07).
 *
 * La inferencia se rompia cuando el Superadministrador corregia el
 * documento de un trabajador (CU08): quien ya habia cambiado su clave
 * volvia a quedar marcado, o al reves.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository    usuarioRepository;
    private final TrabajadorRepository trabajadorRepository;
    private final PasswordEncoder      passwordEncoder;
    private final AuditoriaService     auditoria;

    private static final List<String> ROLES_VALIDOS = List.of(
            "ROLE_TRABAJADOR", "ROLE_SUPERVISOR", "ROLE_JEFE",
            "ROLE_ADMIN", "ROLE_SUPERADMIN");

    /** Complejidad exigida por RN-08 y RNF001. */
    private static final Pattern PASSWORD_VALIDA = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)"
          + "(?=.*[!@#$%^&*()\\-_=+\\[\\]{}|;:,.<>?])[^\\s]{10,20}$");

    @Override
    @Transactional
    public String cambiarRol(Integer idTrabajador, String nuevoRol) {
        if (!ROLES_VALIDOS.contains(nuevoRol))
            throw new BusinessException("Rol invalido: " + nuevoRol);

        Trabajador t = trabajadorRepository.findById(idTrabajador.longValue())
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado."));

        if (!t.isActivo())
            throw new BusinessException("No se puede cambiar el rol de un trabajador inactivo.");

        Usuario u = t.getUsuario();
        if (u == null)
            throw new BusinessException("Este trabajador no tiene cuenta de acceso.");

        String anterior = u.getRol();
        u.setRol(nuevoRol);
        usuarioRepository.save(u);

        auditoria.registrarCampo("usuarios", u.getIdUsuario().longValue(),
                "CAMBIAR_ROL", "rol", anterior, nuevoRol);

        return nuevoRol;
    }

    @Override
    public UsuarioInfoDTO getMiInfo(String username) {
        Usuario u = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        Trabajador t = u.getTrabajador();

        return UsuarioInfoDTO.builder()
                .idUsuario(u.getIdUsuario())
                .username(u.getUsername())
                .rol(u.getRol())
                .idTrabajador(t != null ? t.getIdTrabajador() : null)
                .nombreCompleto(t != null ? t.getNombreCompleto() : u.getUsername())
                .puestoNombre(t != null && t.getPuesto() != null
                        ? t.getPuesto().getPuesto() : null)
                .areaNombre(t != null && t.getArea() != null ? t.getArea().getArea() : null)
                // Campo persistido, no inferido del hash
                .debeCambiarPassword(u.isDebeCambiarPassword())
                .build();
    }

    @Override
    @Transactional
    public void cambiarPassword(String username, String passwordActual, String passwordNueva) {
        Usuario u = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        if (!passwordEncoder.matches(passwordActual, u.getPassword()))
            throw new BusinessException("La contrasena actual es incorrecta.");

        if (passwordEncoder.matches(passwordNueva, u.getPassword()))
            throw new BusinessException("La nueva contrasena debe ser distinta de la actual.");

        if (!PASSWORD_VALIDA.matcher(passwordNueva).matches())
            throw new BusinessException(
                    "La contrasena debe tener entre 10 y 20 caracteres, con al menos "
                            + "una mayuscula, una minuscula, un numero y un caracter especial.");

        u.setPassword(passwordEncoder.encode(passwordNueva));
        // Se libera el bloqueo de cambio obligatorio (RN-07)
        u.setDebeCambiarPassword(false);
        usuarioRepository.save(u);

        auditoria.registrar("usuarios", u.getIdUsuario().longValue(), "CAMBIAR_PASSWORD");
    }
}
