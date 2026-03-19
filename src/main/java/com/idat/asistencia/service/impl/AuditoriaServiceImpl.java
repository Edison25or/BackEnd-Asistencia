package com.idat.asistencia.service.impl;

import com.idat.asistencia.model.entity.Auditoria;
import com.idat.asistencia.repository.AuditoriaRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditoriaServiceImpl implements AuditoriaService {

    private final AuditoriaRepository auditoriaRepo;

    // ── Acción simple (sin campo específico) ─────────────────
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String tabla, Long idRegistro, String accion) {
        persistir(buildBase(tabla, idRegistro, accion));
    }

    // ── Cambio de un campo único ──────────────────────────────
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarCampo(String tabla, Long idRegistro, String accion,
                               String campo, String anterior, String nuevo) {
        if (Objects.equals(anterior, nuevo)) return; // nada cambió

        Auditoria a = buildBase(tabla, idRegistro, accion);
        a.setCampo(campo);
        a.setValorAnterior(anterior);
        a.setValorNuevo(nuevo);
        persistir(a);
    }

    // ── Múltiples cambios en una misma operación ─────────────
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarCambios(String tabla, Long idRegistro,
                                 Map<String, String[]> cambios) {
        List<Auditoria> registros = new ArrayList<>();

        for (Map.Entry<String, String[]> entry : cambios.entrySet()) {
            String anterior = entry.getValue()[0];
            String nuevo    = entry.getValue()[1];

            if (Objects.equals(anterior, nuevo)) continue; // campo sin cambio real

            Auditoria a = buildBase(tabla, idRegistro, "MODIFICAR");
            a.setCampo(entry.getKey());
            a.setValorAnterior(anterior);
            a.setValorNuevo(nuevo);
            registros.add(a);
        }

        if (!registros.isEmpty()) auditoriaRepo.saveAll(registros);
    }

    // ── Consultas ─────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public List<Auditoria> getHistorial(String tabla, Long idRegistro) {
        return auditoriaRepo.findByTablaAndIdRegistroOrderByFechaDesc(tabla, idRegistro);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Auditoria> buscar(String tabla, String accion, Long idUsuario,
                                  LocalDateTime desde, LocalDateTime hasta,
                                  Pageable pageable) {
        return auditoriaRepo.buscar(tabla, accion, idUsuario, desde, hasta, pageable);
    }

    // ── Helpers privados ──────────────────────────────────────

    /**
     * Construye un registro base con los datos del usuario autenticado
     * y la IP del request actual, extraídos del contexto de Spring.
     */
    private Auditoria buildBase(String tabla, Long idRegistro, String accion) {
        Long   idUsuario    = null;
        String nombreUsuario = "sistema";

        // Extraer usuario del SecurityContext
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                nombreUsuario = auth.getName(); // es el username (email)
            }
        } catch (Exception e) {
            log.warn("No se pudo obtener el usuario del SecurityContext: {}", e.getMessage());
        }

        // Extraer IP del request HTTP
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
                .idUsuario(idUsuario)
                .nombreUsuario(nombreUsuario)
                .ipOrigen(ip)
                .fecha(LocalDateTime.now())
                .build();
    }

    private void persistir(Auditoria a) {
        try {
            auditoriaRepo.save(a);
        } catch (Exception e) {
            // La auditoría nunca debe hacer fallar la operación principal
            log.error("Error al guardar registro de auditoría: {}", e.getMessage(), e);
        }
    }
}