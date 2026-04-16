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
 */
@Component
@RequiredArgsConstructor
public class SecurityHelper {

    private final UsuarioRepository usuarioRepository;

    public Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    public boolean esTrabajador() {
        Authentication auth = getAuthentication();
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TRABAJADOR"));
    }

    public boolean esSupervisor() {
        Authentication auth = getAuthentication();
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR"));
    }

    public boolean esJefe() {
        Authentication auth = getAuthentication();
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_JEFE"));
    }

    public String getRol() {
        return getAuthentication().getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority())
                .orElse("");
    }

    public Usuario getUsuarioAutenticado() {
        String username = getAuthentication().getName();
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("Usuario no encontrado: " + username));
    }

    public Long getIdTrabajadorAutenticado() {
        Usuario usuario = getUsuarioAutenticado();
        if (usuario.getTrabajador() == null) {
            throw new BusinessException("El usuario no tiene un trabajador vinculado.");
        }
        return usuario.getTrabajador().getIdTrabajador();
    }

    /**
     * Retorna el idArea del JEFE autenticado (basado en su trabajador → puesto → area).
     * Retorna null si:
     *   - El usuario no tiene trabajador vinculado
     *   - El trabajador no tiene puesto
     *   - El puesto no tiene área
     * Útil para filtrar recursos por área.
     */
    public Integer getIdAreaJefeAutenticado() {
        Usuario usuario = getUsuarioAutenticado();
        if (usuario.getTrabajador() == null) return null;
        if (usuario.getTrabajador().getPuesto() == null) return null;
        if (usuario.getTrabajador().getPuesto().getArea() == null) return null;
        return usuario.getTrabajador().getPuesto().getArea().getIdArea();
    }

    public Long resolverIdTrabajador(Long idTrabajador) {
        if (esTrabajador()) {
            return getIdTrabajadorAutenticado();
        }
        return idTrabajador;
    }

    public void verificarAccesoPropio(Long idTrabajadorSolicitado) {
        if (esTrabajador()) {
            Long idPropio = getIdTrabajadorAutenticado();
            if (!idPropio.equals(idTrabajadorSolicitado)) {
                throw new BusinessException("No tienes permiso para acceder a esta información.");
            }
        }
    }

    public void verificarAccesoPropioOAdmin(Long idTrabajadorSolicitado) {
        String rol = getRol();
        boolean esAdminOSuper = "ROLE_SUPERADMIN".equals(rol) || "ROLE_ADMIN".equals(rol);
        if (esAdminOSuper) return;

        Long idPropio = getIdTrabajadorAutenticado();
        if (!idPropio.equals(idTrabajadorSolicitado)) {
            throw new BusinessException("No tienes permiso para acceder a este perfil.");
        }
    }
}