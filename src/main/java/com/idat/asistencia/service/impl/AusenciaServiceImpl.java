package com.idat.asistencia.service.impl;

import com.idat.asistencia.dto.AusenciaDTOs.*;
import com.idat.asistencia.exception.BusinessException;
import com.idat.asistencia.exception.ResourceNotFoundException;
import com.idat.asistencia.model.entity.*;
import com.idat.asistencia.model.enums.EstadoAsistencia;
import com.idat.asistencia.repository.*;
import com.idat.asistencia.security.SecurityHelper;
import com.idat.asistencia.service.AuditoriaService;
import com.idat.asistencia.service.AusenciaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Permisos y faltas justificadas (CU16, CU17).
 *
 * ============================================================
 * POR QUE EXISTE LA NEUTRALIZACION
 * ============================================================
 * Registrar un permiso no bastaba: el pre-registro del dia seguia en
 * estado PENDIENTE, de modo que el cierre diario lo habria reportado como
 * falta injustificada. Un trabajador con permiso aprobado aparecia
 * faltando (RN-44).
 *
 * neutralizarPorAusencia() se invoca desde tres puntos: al registrar un
 * permiso, al registrar una falta justificada, y al generar pre-registros,
 * porque la ausencia puede haberse registrado antes de programar la
 * semana.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AusenciaServiceImpl implements AusenciaService {

    private final PermisoRepository           permisoRepo;
    private final FaltaJustificadaRepository  faltaRepo;
    private final AsistenciaRepository        asistenciaRepo;
    private final TrabajadorRepository        trabajadorRepo;
    private final TipoAusenciaRepository      tipoAusenciaRepo;
    private final SecurityHelper              securityHelper;
    private final AuditoriaService            auditoria;

    private static final String TABLA_PERMISO = "permisos";
    private static final String TABLA_FALTA   = "faltas_justificadas";

    /** Plazo estandar de anticipacion del permiso, en dias (RN-30). */
    private static final int PLAZO_MIN_DIAS = 7;
    private static final int PLAZO_MAX_DIAS = 14;

    // ============================================================
    // REGISTRAR PERMISO (CU16)
    // ============================================================

    @Override
    @Transactional
    public PermisoResponse registrarPermiso(PermisoRequest req) {
        Trabajador t = buscarTrabajador(req.getIdTrabajador());
        verificarSegregacion(t);

        LocalDate inicio = LocalDate.parse(req.getFechaInicio());
        LocalDate fin    = LocalDate.parse(req.getFechaFin());
        validarRango(inicio, fin);

        TipoAusencia tipo = tipoAusenciaRepo.findById(req.getIdTipoAusencia())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tipo de ausencia no encontrado: " + req.getIdTipoAusencia()));

        // El plazo NO bloquea el registro: lo marca como excepcion
        // visible. El Jefe necesita poder formalizar un permiso pedido
        // con poca anticipacion (RN-30).
        long anticipacion = ChronoUnit.DAYS.between(LocalDate.now(), inicio);
        boolean fueraDePlazo = anticipacion < PLAZO_MIN_DIAS || anticipacion > PLAZO_MAX_DIAS;

        Permiso p = permisoRepo.save(Permiso.builder()
                .trabajador(t)
                .tipoAusencia(tipo)
                .fechaInicio(inicio)
                .fechaFin(fin)
                .comentario(req.getComentario())
                .registradoPor(securityHelper.getUsuarioAutenticado())
                .fechaRegistro(LocalDateTime.now())
                .fueraDePlazo(fueraDePlazo)
                .build());

        int neutralizados = neutralizar(t.getIdTrabajador(), inicio, fin, p, null);

        auditoria.registrarCampo(TABLA_PERMISO, p.getIdPermiso(), "CREAR",
                "permiso", null,
                tipo.getNombre() + " del " + inicio + " al " + fin
                        + (fueraDePlazo ? " (fuera de plazo estandar)" : ""));

        return toResponse(p, neutralizados);
    }

    // ============================================================
    // REGISTRAR FALTA JUSTIFICADA (CU17)
    // ============================================================

    @Override
    @Transactional
    public FaltaJustificadaResponse registrarFaltaJustificada(FaltaJustificadaRequest req) {
        Trabajador t = buscarTrabajador(req.getIdTrabajador());
        verificarSegregacion(t);

        LocalDate inicio = LocalDate.parse(req.getFechaInicio());
        LocalDate fin    = LocalDate.parse(req.getFechaFin());
        validarRango(inicio, fin);

        TipoAusencia tipo = tipoAusenciaRepo.findById(req.getIdTipoAusencia())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tipo de ausencia no encontrado: " + req.getIdTipoAusencia()));

        // Sin limite de plazo (RN-32). Si el periodo ya fue consolidado,
        // el registro se acepta igual pero no altera el consolidado
        // emitido: la neutralizacion excluye lo consolidado.
        FaltaJustificada f = faltaRepo.save(FaltaJustificada.builder()
                .trabajador(t)
                .tipoAusencia(tipo)
                .fechaInicio(inicio)
                .fechaFin(fin)
                .comentario(req.getComentario())
                .registradoPor(securityHelper.getUsuarioAutenticado())
                .fechaRegistro(LocalDateTime.now())
                .build());

        int neutralizados = neutralizar(t.getIdTrabajador(), inicio, fin, null, f);

        auditoria.registrarCampo(TABLA_FALTA, f.getIdFaltaJustificada(), "CREAR",
                "falta_justificada", null,
                tipo.getNombre() + " del " + inicio + " al " + fin);

        return toResponse(f, neutralizados);
    }

    // ============================================================
    // NEUTRALIZACION (RN-44)
    // ============================================================

    /**
     * Enlaza los pre-registros del rango con la ausencia y los deja en
     * estado REVISADO, de modo que el cierre diario no genere falta.
     *
     * Excluye lo consolidado y las quincenas cerradas: una falta
     * justificada registrada despues del cierre queda como informacion
     * historica pero no modifica el consolidado ya emitido (RN-32).
     */
    private int neutralizar(Long idTrabajador, LocalDate desde, LocalDate hasta,
                            Permiso permiso, FaltaJustificada falta) {

        List<Asistencia> afectadas =
                asistenciaRepo.findNeutralizablesEnRango(idTrabajador, desde, hasta);

        for (Asistencia a : afectadas) {
            if (permiso != null) a.setPermiso(permiso);
            if (falta   != null) a.setFaltaJustificada(falta);

            // Solo se neutraliza lo que aun no tiene marcacion. Si el
            // trabajador SI vino ese dia, la jornada real manda sobre la
            // ausencia declarada y la resuelve el Jefe.
            if (a.getIngresoReal() == null) {
                a.setEstado(EstadoAsistencia.REVISADO);
                a.setRequiereRevision(false);
                a.setRevisadoEn(LocalDateTime.now());
            }
            asistenciaRepo.save(a);
        }

        log.info("Ausencia sobre trabajador {} del {} al {}: {} pre-registros neutralizados",
                idTrabajador, desde, hasta, afectadas.size());

        return afectadas.size();
    }

    /**
     * Aplica ausencias YA registradas sobre pre-registros recien creados.
     * Cubre el caso de un permiso pedido antes de que el Jefe programara
     * la semana.
     */
    @Override
    @Transactional
    public int neutralizarPorAusenciasExistentes(Long idTrabajador,
                                                 LocalDate desde, LocalDate hasta) {
        int total = 0;

        for (Permiso p : permisoRepo.findPorTrabajadorYRango(idTrabajador, desde, hasta)) {
            total += neutralizar(idTrabajador,
                    maxFecha(p.getFechaInicio(), desde),
                    minFecha(p.getFechaFin(), hasta), p, null);
        }

        for (FaltaJustificada f : faltaRepo.findPorTrabajadorYRango(idTrabajador, desde, hasta)) {
            total += neutralizar(idTrabajador,
                    maxFecha(f.getFechaInicio(), desde),
                    minFecha(f.getFechaFin(), hasta), null, f);
        }

        return total;
    }

    // ============================================================
    // ELIMINACION (RN-32, RN-44)
    // ============================================================

    @Override
    @Transactional
    public int eliminarPermiso(Long idPermiso) {
        Permiso p = permisoRepo.findById(idPermiso)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Permiso no encontrado: " + idPermiso));

        verificarSegregacion(p.getTrabajador());

        // Un consolidado ya emitido no se altera (RN-32). Ademas, la
        // clave foranea abortaria el borrado y el error saldria como
        // fallo de base de datos.
        long bloqueadas = asistenciaRepo.countBloqueadasPorPermiso(idPermiso);
        if (bloqueadas > 0)
            throw new BusinessException(
                    "No se puede eliminar: " + bloqueadas + " jornada(s) de este permiso "
                            + "ya fueron consolidadas o pertenecen a una quincena cerrada. "
                            + "Reabre la quincena si necesitas corregirlo.");

        int revertidos = revertir(p.getTrabajador().getIdTrabajador(),
                p.getFechaInicio(), p.getFechaFin(), idPermiso, null);

        permisoRepo.delete(p);

        auditoria.registrarCampo(TABLA_PERMISO, idPermiso, "ELIMINAR",
                "permiso",
                p.getTipoAusencia().getNombre() + " del " + p.getFechaInicio()
                        + " al " + p.getFechaFin(),
                null);
        auditoria.registrarConMotivo(TABLA_PERMISO, idPermiso, "ELIMINAR",
                revertidos + " pre-registro(s) volvieron a pendiente");

        return revertidos;
    }

    @Override
    @Transactional
    public int eliminarFaltaJustificada(Long idFalta) {
        FaltaJustificada f = faltaRepo.findById(idFalta)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Falta justificada no encontrada: " + idFalta));

        verificarSegregacion(f.getTrabajador());

        long bloqueadas = asistenciaRepo.countBloqueadasPorFalta(idFalta);
        if (bloqueadas > 0)
            throw new BusinessException(
                    "No se puede eliminar: " + bloqueadas + " jornada(s) de esta falta "
                            + "justificada ya fueron consolidadas o pertenecen a una "
                            + "quincena cerrada. Reabre la quincena si necesitas corregirlo.");

        int revertidos = revertir(f.getTrabajador().getIdTrabajador(),
                f.getFechaInicio(), f.getFechaFin(), null, idFalta);

        faltaRepo.delete(f);

        auditoria.registrarCampo(TABLA_FALTA, idFalta, "ELIMINAR",
                "falta_justificada",
                f.getTipoAusencia().getNombre() + " del " + f.getFechaInicio()
                        + " al " + f.getFechaFin(),
                null);
        auditoria.registrarConMotivo(TABLA_FALTA, idFalta, "ELIMINAR",
                revertidos + " pre-registro(s) volvieron a pendiente");

        return revertidos;
    }

    /**
     * Deshace la neutralizacion antes de borrar la ausencia.
     *
     * Hay que soltar la referencia desde Asistencia ANTES del delete: la
     * clave foranea lo impediria y el error saldria como un fallo de base
     * de datos, no como un mensaje entendible.
     *
     * Una jornada que el trabajador SI marco conserva su marcacion: se le
     * quita la referencia a la ausencia y nada mas. Devolverla a PENDIENTE
     * borraria un hecho ocurrido.
     */
    private int revertir(Long idTrabajador, LocalDate desde, LocalDate hasta,
                         Long idPermiso, Long idFalta) {

        List<Asistencia> afectadas =
                asistenciaRepo.findNeutralizablesEnRango(idTrabajador, desde, hasta);

        int revertidos = 0;

        for (Asistencia a : afectadas) {
            boolean tocaEsta =
                    (idPermiso != null && a.getPermiso() != null
                            && idPermiso.equals(a.getPermiso().getIdPermiso()))
                 || (idFalta   != null && a.getFaltaJustificada() != null
                            && idFalta.equals(a.getFaltaJustificada().getIdFaltaJustificada()));

            if (!tocaEsta) continue;

            if (idPermiso != null) a.setPermiso(null);
            if (idFalta   != null) a.setFaltaJustificada(null);

            // Solo vuelve a pendiente lo que nunca se marco. El cierre
            // diario la reevaluara y, si su ventana ya vencio, la
            // reportara como falta injustificada, que es lo correcto una
            // vez retirada la justificacion.
            if (a.getIngresoReal() == null) {
                a.setEstado(EstadoAsistencia.PENDIENTE);
                a.setRequiereRevision(false);
                a.setRevisadoEn(null);
                revertidos++;
            }

            asistenciaRepo.save(a);
        }

        log.info("Ausencia eliminada del trabajador {}: {} pre-registros revertidos",
                idTrabajador, revertidos);

        return revertidos;
    }

    // ============================================================
    // CONSULTAS
    // ============================================================

    @Override
    public List<PermisoResponse> listarPermisos(Long idTrabajador,
                                                LocalDate desde, LocalDate hasta) {
        return permisoRepo.findPorTrabajadorYRango(idTrabajador, desde, hasta)
                .stream().map(p -> toResponse(p, 0)).collect(Collectors.toList());
    }

    @Override
    public List<FaltaJustificadaResponse> listarFaltasJustificadas(
            Long idTrabajador, LocalDate desde, LocalDate hasta) {
        return faltaRepo.findPorTrabajadorYRango(idTrabajador, desde, hasta)
                .stream().map(f -> toResponse(f, 0)).collect(Collectors.toList());
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private Trabajador buscarTrabajador(Long id) {
        return trabajadorRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado: " + id));
    }

    private void validarRango(LocalDate inicio, LocalDate fin) {
        if (fin.isBefore(inicio))
            throw new BusinessException("La fecha de fin no puede ser anterior a la de inicio.");
        if (ChronoUnit.DAYS.between(inicio, fin) > 365)
            throw new BusinessException("El rango no puede superar un ano.");
    }

    /**
     * Segregacion de funciones (RN-01): el actor debe estar en un nivel
     * estrictamente superior al del afectado.
     *
     * La validacion vive en el servidor y no solo en la interfaz, porque
     * ocultar el boton no impide llamar al endpoint (RNF003).
     */
    private void verificarSegregacion(Trabajador afectado) {
        int nivelActor    = nivelDe(securityHelper.getRol());
        int nivelAfectado = nivelDe(afectado.getUsuario() != null
                ? afectado.getUsuario().getRol() : "ROLE_TRABAJADOR");

        // El Superadministrador es la unica excepcion y queda auditado.
        if (nivelActor == 5) return;

        Long idActor = securityHelper.getUsuarioAutenticado().getTrabajador() != null
                ? securityHelper.getUsuarioAutenticado().getTrabajador().getIdTrabajador() : null;

        if (afectado.getIdTrabajador().equals(idActor))
            throw new BusinessException(
                    "No puedes registrar una ausencia sobre tu propio registro. "
                            + "La accion corresponde al nivel inmediato superior.");

        if (nivelActor <= nivelAfectado)
            throw new BusinessException(
                    "No puedes registrar una ausencia de alguien de tu mismo nivel jerarquico "
                            + "o superior. La accion corresponde al nivel inmediato superior.");
    }

    private int nivelDe(String rol) {
        if (rol == null) return 1;
        return switch (rol) {
            case "ROLE_SUPERADMIN" -> 5;
            case "ROLE_ADMIN"      -> 4;
            case "ROLE_JEFE"       -> 3;
            case "ROLE_SUPERVISOR" -> 2;
            default                -> 1;
        };
    }

    private LocalDate maxFecha(LocalDate a, LocalDate b) { return a.isAfter(b)  ? a : b; }
    private LocalDate minFecha(LocalDate a, LocalDate b) { return a.isBefore(b) ? a : b; }

    private PermisoResponse toResponse(Permiso p, int neutralizados) {
        return PermisoResponse.builder()
                .idPermiso(p.getIdPermiso())
                .idTrabajador(p.getTrabajador().getIdTrabajador())
                .trabajadorNombre(p.getTrabajador().getNombreCompleto())
                .tipoAusencia(p.getTipoAusencia().getNombre())
                .fechaInicio(p.getFechaInicio().toString())
                .fechaFin(p.getFechaFin().toString())
                .comentario(p.getComentario())
                .fueraDePlazo(p.isFueraDePlazo())
                .registradoPor(p.getRegistradoPor() != null
                        ? p.getRegistradoPor().getUsername() : null)
                .fechaRegistro(p.getFechaRegistro().toString())
                .preRegistrosNeutralizados(neutralizados)
                .build();
    }

    private FaltaJustificadaResponse toResponse(FaltaJustificada f, int neutralizados) {
        return FaltaJustificadaResponse.builder()
                .idFaltaJustificada(f.getIdFaltaJustificada())
                .idTrabajador(f.getTrabajador().getIdTrabajador())
                .trabajadorNombre(f.getTrabajador().getNombreCompleto())
                .tipoAusencia(f.getTipoAusencia().getNombre())
                .fechaInicio(f.getFechaInicio().toString())
                .fechaFin(f.getFechaFin().toString())
                .comentario(f.getComentario())
                .registradoPor(f.getRegistradoPor() != null
                        ? f.getRegistradoPor().getUsername() : null)
                .fechaRegistro(f.getFechaRegistro().toString())
                .preRegistrosNeutralizados(neutralizados)
                .build();
    }
}
