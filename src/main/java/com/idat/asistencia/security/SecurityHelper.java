package com.idat.asistencia.security;

import com.idat.asistencia.exception.BusinessException;
import com.idat.asistencia.model.entity.Usuario;
import com.idat.asistencia.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Utilidad centralizada para resolución de identidad del usuario autenticado.
 * Evita repetir el patrón "es TRABAJADOR → forzar su propio ID" en cada service.
 */
@Component
@RequiredArgsConstructor
public class SecurityHelper {

    private final UsuarioRepository usuarioRepository;

    /**
     * Retorna la autenticación actual del contexto de seguridad.
     */
    public Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * Retorna true si el usuario autenticado tiene el rol ROLE_TRABAJADOR.
     */
    public boolean esTrabajador() {
        Authentication auth = getAuthentication();
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TRABAJADOR"));
    }

    /**
     * Retorna true si el usuario autenticado tiene el rol ROLE_SUPERVISOR.
     */
    public boolean esSupervisor() {
        Authentication auth = getAuthentication();
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR"));
    }

    /**
     * Retorna el rol del usuario autenticado (e.g. "ROLE_SUPERADMIN").
     */
    public String getRol() {
        return getAuthentication().getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority())
                .orElse("");
    }

    /**
     * Retorna el Usuario (entidad) del usuario autenticado.
     */
    public Usuario getUsuarioAutenticado() {
        String username = getAuthentication().getName();
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("Usuario no encontrado: " + username));
    }

    /**
     * Retorna el idTrabajador del usuario autenticado.
     * Lanza excepción si el usuario no tiene trabajador vinculado.
     */
    public Long getIdTrabajadorAutenticado() {
        Usuario usuario = getUsuarioAutenticado();
        if (usuario.getTrabajador() == null) {
            throw new BusinessException("El usuario no tiene un trabajador vinculado.");
        }
        return usuario.getTrabajador().getIdTrabajador();
    }

    /**
     * Si el usuario es TRABAJADOR, ignora el idTrabajador recibido y devuelve
     * el id del trabajador autenticado. Para otros roles, devuelve el valor original.
     *
     * Patrón reutilizable para endpoints que comparten TRABAJADOR y roles superiores.
     */
    public Long resolverIdTrabajador(Long idTrabajador) {
        if (esTrabajador()) {
            return getIdTrabajadorAutenticado();
        }
        return idTrabajador;
    }

    /**
     * Verifica que el idTrabajador solicitado pertenezca al usuario autenticado.
     * Si no coincide, lanza BusinessException.
     * Solo aplica cuando el usuario es TRABAJADOR.
     */
    public void verificarAccesoPropio(Long idTrabajadorSolicitado) {
        if (esTrabajador()) {
            Long idPropio = getIdTrabajadorAutenticado();
            if (!idPropio.equals(idTrabajadorSolicitado)) {
                throw new BusinessException("No tienes permiso para acceder a esta información.");
            }
        }
    }
}
