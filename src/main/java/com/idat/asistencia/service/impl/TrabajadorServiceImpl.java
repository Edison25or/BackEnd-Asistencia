package com.idat.asistencia.service.impl;

import com.idat.asistencia.dto.TrabajadorRequestDTO;
import com.idat.asistencia.dto.TrabajadorResponseDTO;
import com.idat.asistencia.exception.BusinessException;
import com.idat.asistencia.exception.ResourceNotFoundException;
import com.idat.asistencia.mapper.TrabajadorMapper;
import com.idat.asistencia.model.entity.*;
import com.idat.asistencia.model.enums.EstadoTrabajador;
import com.idat.asistencia.repository.*;
import com.idat.asistencia.security.SecurityHelper;
import com.idat.asistencia.service.AuditoriaService;
import com.idat.asistencia.service.TrabajadorService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrabajadorServiceImpl implements TrabajadorService {

    private final TrabajadorRepository      trabajadorRepository;
    private final PuestoRepository          puestoRepository;
    private final GeneroRepository          generoRepository;
    private final PeriodoLaboralRepository  periodoLaboralRepository;
    private final HistorialPuestoRepository historialPuestoRepository;
    private final TrabajadorMapper          trabajadorMapper;
    private final PasswordEncoder           passwordEncoder;
    private final GrupoTrabajoRepository    grupoRepo;
    private final AuditoriaService          auditoriaService;
    private final SecurityHelper            securityHelper;

    private static final String TABLA = "trabajadores";

    // ── CREAR ─────────────────────────────────────────────────
    @Override
    @Transactional
    public TrabajadorResponseDTO crearTrabajador(TrabajadorRequestDTO dto) {
        Trabajador trabajador = trabajadorMapper.toEntity(dto);
        trabajador.setEstado(EstadoTrabajador.ACTIVO);

        Usuario usuario = Usuario.builder()
                .username(dto.getEmail())
                .password(passwordEncoder.encode(dto.getNroDocumento()))
                .rol("ROLE_TRABAJADOR")
                .trabajador(trabajador)
                .build();
        trabajador.setUsuario(usuario);

        Trabajador guardado = trabajadorRepository.save(trabajador);

        periodoLaboralRepository.save(PeriodoLaboral.builder()
                .trabajador(guardado).fechaIngreso(LocalDate.now()).build());
        historialPuestoRepository.save(HistorialPuesto.builder()
                .trabajador(guardado).puesto(guardado.getPuesto())
                .fechaInicio(LocalDate.now()).motivoCambio("Contratación inicial").build());

        auditoriaService.registrar(TABLA, guardado.getIdTrabajador(), "CREAR");

        return trabajadorMapper.toDto(guardado);
    }

    // ── ACTUALIZAR ────────────────────────────────────────────
    @Override
    @Transactional
    public TrabajadorResponseDTO actualizarTrabajador(Long id, TrabajadorRequestDTO dto,
                                                      String rolEditor) {

        // Si es TRABAJADOR, verificar que solo edite su propio perfil
        if ("ROLE_TRABAJADOR".equals(rolEditor)) {
            securityHelper.verificarAccesoPropio(id);
        }

        Trabajador t = trabajadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado: " + id));

        boolean esSuperAdmin = "ROLE_SUPERADMIN".equals(rolEditor);
        boolean emailCambio  = !t.getEmail().equals(dto.getEmail());
        boolean docCambio    = !t.getNroDocumento().equals(dto.getNroDocumento());
        boolean tipoCambio   = !t.getDocIdentidad().equals(dto.getDocIdentidad());

        if ((emailCambio || docCambio || tipoCambio) && !esSuperAdmin)
            throw new BusinessException(
                    "No tienes permiso para modificar email, tipo o número de documento.");

        if (emailCambio && trabajadorRepository.existsByEmail(dto.getEmail()))
            throw new BusinessException("El email " + dto.getEmail() + " ya está en uso.");
        if (docCambio && trabajadorRepository.existsByNroDocumento(dto.getNroDocumento()))
            throw new BusinessException("El documento " + dto.getNroDocumento() + " ya está en uso.");

        Map<String, String[]> cambios = new LinkedHashMap<>();
        capturar(cambios, "docIdentidad",         t.getDocIdentidad(),        dto.getDocIdentidad());
        capturar(cambios, "nroDocumento",          t.getNroDocumento(),         dto.getNroDocumento());
        capturar(cambios, "pNombre",               t.getPNombre(),             dto.getPNombre());
        capturar(cambios, "sNombre",               strOrNull(t.getSNombre()),  strOrNull(dto.getSNombre()));
        capturar(cambios, "aPaterno",              t.getAPaterno(),            dto.getAPaterno());
        capturar(cambios, "aMaterno",              t.getAMaterno(),            dto.getAMaterno());
        capturar(cambios, "fechaNac",              strOrNull(t.getFechaNac()), strOrNull(dto.getFechaNac()));
        capturar(cambios, "email",                 t.getEmail(),               dto.getEmail());
        capturar(cambios, "telefono",              strOrNull(t.getTelefono()), strOrNull(dto.getTelefono()));
        capturar(cambios, "direccion",             strOrNull(t.getDireccion()),strOrNull(dto.getDireccion()));
        capturar(cambios, "idPuesto",
                strOrNull(t.getPuesto() != null ? t.getPuesto().getIdPuesto() : null),
                strOrNull(dto.getIdPuesto()));
        capturar(cambios, "idGenero",
                strOrNull(t.getGenero() != null ? t.getGenero().getIdGenero() : null),
                strOrNull(dto.getIdGenero()));

        String dniOriginal = t.getNroDocumento();

        Puesto puesto = puestoRepository.findById(dto.getIdPuesto())
                .orElseThrow(() -> new ResourceNotFoundException("Puesto no encontrado"));
        Genero genero = generoRepository.findById(dto.getIdGenero())
                .orElseThrow(() -> new ResourceNotFoundException("Género no encontrado"));

        t.setDocIdentidad(dto.getDocIdentidad());
        t.setNroDocumento(dto.getNroDocumento());
        t.setPNombre(dto.getPNombre());
        t.setSNombre(dto.getSNombre());
        t.setAPaterno(dto.getAPaterno());
        t.setAMaterno(dto.getAMaterno());
        t.setFechaNac(dto.getFechaNac());
        t.setDireccion(dto.getDireccion());
        t.setTelefono(dto.getTelefono());
        t.setEmail(dto.getEmail());
        t.setContactoEmergencias(dto.getContactoEmergencias());
        t.setNroContacto(dto.getNroContacto());
        t.setParentesco(dto.getParentesco());
        t.setPuesto(puesto);
        t.setGenero(genero);

        StringBuilder mensajeCredencial = new StringBuilder();
        if (esSuperAdmin && t.getUsuario() != null) {
            Usuario usuario = t.getUsuario();
            if (emailCambio) {
                usuario.setUsername(dto.getEmail());
                mensajeCredencial.append("La nueva credencial del trabajador: ")
                        .append(dto.getEmail()).append(".");
            }
            if (docCambio && passwordEncoder.matches(dniOriginal, usuario.getPassword())) {
                usuario.setPassword(passwordEncoder.encode(dto.getNroDocumento()));
                if (mensajeCredencial.length() > 0) mensajeCredencial.append(" ");
                mensajeCredencial.append("Su nueva contraseña es el número de documento actualizado.");
            }
        }

        Trabajador guardado = trabajadorRepository.save(t);

        auditoriaService.registrarCambios(TABLA, guardado.getIdTrabajador(), cambios);

        TrabajadorResponseDTO respuesta = trabajadorMapper.toDto(guardado);
        if (guardado.getUsuario() != null) respuesta.setRol(guardado.getUsuario().getRol());
        if (mensajeCredencial.length() > 0) respuesta.setMensajeCredencial(mensajeCredencial.toString());
        return respuesta;
    }

    // ── CONSULTAS ─────────────────────────────────────────────
    @Override
    public Page<TrabajadorResponseDTO> obtenerTodosLosTrabajadores(EstadoTrabajador estado,
                                                                   Pageable pageable) {
        return trabajadorRepository.findAllByEstado(estado, pageable)
                .map(t -> {
                    TrabajadorResponseDTO dto = trabajadorMapper.toDto(t);
                    if (t.getUsuario() != null) dto.setRol(t.getUsuario().getRol());
                    poblarGrupo(dto, t.getIdTrabajador());
                    return dto;
                });
    }

    @Override
    public TrabajadorResponseDTO obtenerTrabajadorById(Long id) {
        // V1 FIX: Si es TRABAJADOR, solo puede consultar su propio perfil
        securityHelper.verificarAccesoPropio(id);

        Trabajador t = trabajadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado: " + id));
        TrabajadorResponseDTO dto = trabajadorMapper.toDto(t);
        if (t.getUsuario() != null) dto.setRol(t.getUsuario().getRol());
        poblarGrupo(dto, t.getIdTrabajador());
        return dto;
    }

    @Override
    public Page<TrabajadorResponseDTO> buscarTrabajadores(String q, EstadoTrabajador estado,
                                                          Pageable pageable) {
        // V2 FIX: Si es TRABAJADOR, solo puede encontrar su propio perfil
        if (securityHelper.esTrabajador()) {
            Long idPropio = securityHelper.getIdTrabajadorAutenticado();
            Trabajador t = trabajadorRepository.findById(idPropio)
                    .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado."));
            TrabajadorResponseDTO dto = trabajadorMapper.toDto(t);
            return new org.springframework.data.domain.PageImpl<>(
                    List.of(dto), pageable, 1);
        }

        return trabajadorRepository.buscarPorTermino(q, estado, pageable)
                .map(trabajadorMapper::toDto);
    }

    // ── CESAR ─────────────────────────────────────────────────
    @Override
    @Transactional
    public void cesarTrabajador(Long id, String motivo) {
        Trabajador t = trabajadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado: " + id));

        t.setEstado(EstadoTrabajador.INACTIVO);
        if (t.getUsuario() != null) t.getUsuario().setEnabled(false);

        periodoLaboralRepository.findPeriodoActivo(id).ifPresent(p -> {
            p.setFechaCese(LocalDate.now());
            p.setMotivoCese(motivo);
            periodoLaboralRepository.save(p);
        });
        historialPuestoRepository.findPuestoActivo(id).ifPresent(h -> {
            h.setFechaFin(LocalDate.now());
            h.setMotivoCambio("Cese del trabajador: " + motivo);
            historialPuestoRepository.save(h);
        });

        trabajadorRepository.save(t);
        auditoriaService.registrarCampo(TABLA, id, "CESAR", "motivo", null, motivo);
    }

    // ── REINGRESAR ────────────────────────────────────────────
    @Override
    @Transactional
    public TrabajadorResponseDTO reingresarTrabajador(Long id, Integer idPuesto) {
        Trabajador t = trabajadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado: " + id));

        if (t.getEstado() == EstadoTrabajador.ACTIVO)
            throw new BusinessException("El trabajador ya se encuentra ACTIVO.");

        Puesto nuevoPuesto = puestoRepository.findById(idPuesto)
                .orElseThrow(() -> new ResourceNotFoundException("Puesto no encontrado"));

        t.setEstado(EstadoTrabajador.ACTIVO);
        t.setPuesto(nuevoPuesto);
        if (t.getUsuario() != null) t.getUsuario().setEnabled(true);

        periodoLaboralRepository.save(PeriodoLaboral.builder()
                .trabajador(t).fechaIngreso(LocalDate.now()).build());
        historialPuestoRepository.save(HistorialPuesto.builder()
                .trabajador(t).puesto(nuevoPuesto)
                .fechaInicio(LocalDate.now()).motivoCambio("Reingreso a la empresa").build());

        Trabajador guardado = trabajadorRepository.save(t);
        auditoriaService.registrarCampo(TABLA, id, "REINGRESAR",
                "idPuesto", null, String.valueOf(idPuesto));

        TrabajadorResponseDTO dto = trabajadorMapper.toDto(guardado);
        if (guardado.getUsuario() != null) dto.setRol(guardado.getUsuario().getRol());
        return dto;
    }

    // ── RESET PASSWORD ────────────────────────────────────────
    @Override
    @Transactional
    public void resetearPassword(Long id) {
        Trabajador t = trabajadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado: " + id));

        if (t.getUsuario() == null)
            throw new BusinessException("Este trabajador no tiene cuenta de acceso.");

        t.getUsuario().setPassword(passwordEncoder.encode(t.getNroDocumento()));
        trabajadorRepository.save(t);
        auditoriaService.registrar(TABLA, id, "RESET_PASSWORD");
    }

    // ── HELPERS ───────────────────────────────────────────────
    private void poblarGrupo(TrabajadorResponseDTO dto, Long idTrabajador) {
        grupoRepo.findByTrabajadorId(idTrabajador).ifPresent(g -> {
            dto.setGrupoActualId(g.getIdGrupo());
            dto.setGrupoActualNombre(g.getNombre());
        });
    }

    private void capturar(Map<String, String[]> mapa, String campo,
                          String anterior, String nuevo) {
        mapa.put(campo, new String[]{ anterior, nuevo });
    }

    private String strOrNull(Object o) {
        return o == null ? null : o.toString();
    }
}