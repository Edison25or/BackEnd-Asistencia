package com.idat.asistencia.service.impl;

import com.idat.asistencia.model.entity.Auditoria;
import com.idat.asistencia.model.entity.Usuario;
import com.idat.asistencia.repository.AuditoriaRepository;
import com.idat.asistencia.repository.UsuarioRepository;
import com.idat.asistencia.service.AuditoriaService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registro de acciones sensibles (RN-02, CU28).
 *
 * ============================================================
 * CORRECCION PRINCIPAL
 * ============================================================
 * buildBase() declaraba una variable idUsuario que NUNCA asignaba: se
 * inicializaba en null y se pasaba tal cual al constructor. Todo el
 * historial de auditoria quedaba sin autor, lo que hacia inutil el filtro
 * por usuario de CU28 y vaciaba de sentido a la trazabilidad.
 *
 * Ahora se resuelve el usuario autenticado y se guarda la relacion real,
 * ademas del nombre en instantanea que sobrevive a un cambio de nombre.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditoriaServiceImpl implements AuditoriaService {

    private final AuditoriaRepository auditoriaRepo;
    private final UsuarioRepository   usuarioRepo;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String tabla, Long idRegistro, String accion) {
        persistir(buildBase(tabla, idRegistro, accion));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarCampo(String tabla, Long idRegistro, String accion,
                               String campo, String anterior, String nuevo) {
        if (Objects.equals(anterior, nuevo)) return;

        Auditoria a = buildBase(tabla, idRegistro, accion);
        a.setCampo(campo);
        a.setValorAnterior(anterior);
        a.setValorNuevo(nuevo);
        persistir(a);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarConMotivo(String tabla, Long idRegistro,
                                   String accion, String motivo) {
        Auditoria a = buildBase(tabla, idRegistro, accion);
        a.setMotivo(motivo);
        persistir(a);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarCambios(String tabla, Long idRegistro,
                                 Map<String, String[]> cambios) {
        List<Auditoria> registros = new ArrayList<>();

        for (Map.Entry<String, String[]> entry : cambios.entrySet()) {
            String anterior = entry.getValue()[0];
            String nuevo    = entry.getValue()[1];
            if (Objects.equals(anterior, nuevo)) continue;

            Auditoria a = buildBase(tabla, idRegistro, "MODIFICAR");
            a.setCampo(entry.getKey());
            a.setValorAnterior(anterior);
            a.setValorNuevo(nuevo);
            registros.add(a);
        }

        if (!registros.isEmpty()) {
            try {
                auditoriaRepo.saveAll(registros);
            } catch (Exception e) {
                log.error("Error al guardar auditoria: {}", e.getMessage(), e);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Auditoria> getHistorial(String tabla, Long idRegistro) {
        return auditoriaRepo.findByTablaAndIdRegistroOrderByFechaDesc(tabla, idRegistro);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Auditoria> buscar(String tabla, String accion, Integer idUsuario,
                                  LocalDateTime desde, LocalDateTime hasta,
                                  Pageable pageable) {
        return auditoriaRepo.buscar(tabla, accion, idUsuario, desde, hasta, pageable);
    }

    // ---------- Helpers ----------

    private Auditoria buildBase(String tabla, Long idRegistro, String accion) {
        Usuario usuario       = null;
        String  nombreUsuario = "sistema";

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getPrincipal())) {
                nombreUsuario = auth.getName();
                // Esta es la asignacion que faltaba
                usuario = usuarioRepo.findByUsername(auth.getName()).orElse(null);
            }
        } catch (Exception e) {
            log.warn("No se pudo obtener el usuario del contexto: {}", e.getMessage());
        }

        String ip = null;
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                ip = req.getHeader("X-Forwarded-For");
                if (ip == null || ip.isBlank()) ip = req.getRemoteAddr();
            }
        } catch (Exception e) {
            log.warn("No se pudo obtener la IP: {}", e.getMessage());
        }

        return Auditoria.builder()
                .tabla(tabla)
                .idRegistro(idRegistro)
                .accion(accion)
                .usuario(usuario)
                .nombreUsuario(nombreUsuario)
                .ipOrigen(ip)
                .fecha(LocalDateTime.now())
                .build();
    }

    private void persistir(Auditoria a) {
        try {
            auditoriaRepo.save(a);
        } catch (Exception e) {
            // La auditoria nunca debe hacer fallar la operacion principal
            log.error("Error al guardar registro de auditoria: {}", e.getMessage(), e);
        }
    }
}
