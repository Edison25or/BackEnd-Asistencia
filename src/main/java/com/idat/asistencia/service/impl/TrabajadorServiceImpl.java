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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gestion de personal (CU07 a CU11).
 *
 * ============================================================
 * QUE CAMBIA
 * ============================================================
 * 1. Se genera el codigo de barras al crear y al reingresar (CU11).
 *    El prototipo no tenia ningun campo de codigo.
 *
 * 2. La autoedicion queda restringida a datos de contacto (RN-06). El
 *    prototipo dejaba que Jefe, Supervisor y Trabajador editaran su
 *    propio perfil COMPLETO, incluidos nombre, fecha de nacimiento y
 *    puesto.
 *
 * 3. El cese valida que no existan marcaciones posteriores a la fecha
 *    (RN-10), lo que faltaba por completo, y el motivo pasa de texto
 *    libre a catalogo (RN-11).
 *
 * 4. El reingreso conserva area y puesto salvo indicacion explicita
 *    (RN-12). El prototipo exigia el puesto siempre.
 *
 * 5. El restablecimiento de contrasena activa el indicador persistido
 *    debeCambiarPassword (RN-07).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrabajadorServiceImpl implements TrabajadorService {

    private final TrabajadorRepository      trabajadorRepository;
    private final PuestoRepository          puestoRepository;
    private final GeneroRepository          generoRepository;
    private final PeriodoLaboralRepository  periodoLaboralRepository;
    private final HistorialPuestoRepository historialPuestoRepository;
    private final MotivoCeseRepository      motivoCeseRepository;
    private final AsistenciaRepository      asistenciaRepository;
    private final TrabajadorMapper          trabajadorMapper;
    private final PasswordEncoder           passwordEncoder;
    private final AuditoriaService          auditoriaService;
    private final SecurityHelper            securityHelper;

    private static final String TABLA = "trabajadores";

    // ════════════════════════════════════════════════════════════
    // CREAR (CU07)
    // ════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public TrabajadorResponseDTO crearTrabajador(TrabajadorRequestDTO dto) {
        if (trabajadorRepository.existsByNroDocumento(dto.getNroDocumento()))
            throw new BusinessException(
                    "Ya existe un trabajador con el documento " + dto.getNroDocumento());

        if (trabajadorRepository.existsByEmail(dto.getEmail()))
            throw new BusinessException(
                    "Ya existe un trabajador con el email " + dto.getEmail());

        validarFormatoDocumento(dto.getDocIdentidad(), dto.getNroDocumento());

        if (dto.getFechaNac().plusYears(18).isAfter(LocalDate.now()))
            throw new BusinessException("El trabajador debe tener al menos 18 anos.");

        Trabajador trabajador = trabajadorMapper.toEntity(dto);
        trabajador.setEstado(EstadoTrabajador.ACTIVO);

        Usuario usuario = Usuario.builder()
                .username(dto.getEmail())
                .password(passwordEncoder.encode(dto.getNroDocumento()))
                .rol("ROLE_TRABAJADOR")   // sin excepcion (RN-03)
                .trabajador(trabajador)
                .enabled(true)
                .debeCambiarPassword(true)   // campo persistido (RN-07)
                .build();
        trabajador.setUsuario(usuario);

        Trabajador guardado = trabajadorRepository.save(trabajador);

        // El codigo se genera despues de persistir, porque necesita el
        // identificador ya asignado por la secuencia.
        asignarCodigoBarras(guardado);
        guardado = trabajadorRepository.save(guardado);

        periodoLaboralRepository.save(PeriodoLaboral.builder()
                .trabajador(guardado).fechaIngreso(LocalDate.now()).build());
        historialPuestoRepository.save(HistorialPuesto.builder()
                .trabajador(guardado).puesto(guardado.getPuesto())
                .fechaInicio(LocalDate.now()).motivoCambio("Contratacion inicial").build());

        auditoriaService.registrar(TABLA, guardado.getIdTrabajador(), "CREAR");

        return toDto(guardado);
    }

    // ════════════════════════════════════════════════════════════
    // ACTUALIZAR (CU08)
    // ════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public TrabajadorResponseDTO actualizarTrabajador(Long id, TrabajadorRequestDTO dto,
                                                      String rolEditor) {
        securityHelper.verificarAccesoPropioOAdmin(id);

        Trabajador t = trabajadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado: " + id));

        boolean esSuperAdmin = "ROLE_SUPERADMIN".equals(rolEditor);
        boolean esAdmin      = "ROLE_ADMIN".equals(rolEditor) || esSuperAdmin;

        // ---- Autoedicion limitada (RN-06) ----
        // Quien no es Administrador solo puede tocar sus datos de
        // contacto. La restriccion corre en el servidor: bloquear los
        // campos en la interfaz no impide llamar al endpoint.
        if (!esAdmin) {
            return actualizarSoloContacto(t, dto);
        }

        boolean emailCambio = !t.getEmail().equals(dto.getEmail());
        boolean docCambio   = !t.getNroDocumento().equals(dto.getNroDocumento());
        boolean tipoCambio  = !t.getDocIdentidad().equals(dto.getDocIdentidad());

        // ---- Datos sensibles: solo Superadministrador (RN-05) ----
        if ((emailCambio || docCambio || tipoCambio) && !esSuperAdmin)
            throw new BusinessException(
                    "No tienes permiso para modificar el email, el tipo o el numero de documento.");

        if (emailCambio && trabajadorRepository.existsByEmail(dto.getEmail()))
            throw new BusinessException("El email " + dto.getEmail() + " ya esta en uso.");
        if (docCambio && trabajadorRepository.existsByNroDocumento(dto.getNroDocumento()))
            throw new BusinessException("El documento " + dto.getNroDocumento() + " ya esta en uso.");

        validarFormatoDocumento(dto.getDocIdentidad(), dto.getNroDocumento());

        if (dto.getFechaNac().plusYears(18).isAfter(LocalDate.now()))
            throw new BusinessException("El trabajador debe tener al menos 18 anos.");

        Map<String, String[]> cambios = new LinkedHashMap<>();
        capturar(cambios, "docIdentidad", t.getDocIdentidad(), dto.getDocIdentidad());
        capturar(cambios, "nroDocumento", t.getNroDocumento(), dto.getNroDocumento());
        capturar(cambios, "pNombre",      t.getPNombre(),      dto.getPNombre());
        capturar(cambios, "sNombre",      str(t.getSNombre()), str(dto.getSNombre()));
        capturar(cambios, "aPaterno",     t.getAPaterno(),     dto.getAPaterno());
        capturar(cambios, "aMaterno",     t.getAMaterno(),     dto.getAMaterno());
        capturar(cambios, "fechaNac",     str(t.getFechaNac()), str(dto.getFechaNac()));
        capturar(cambios, "email",        t.getEmail(),        dto.getEmail());
        capturar(cambios, "telefono",     str(t.getTelefono()), str(dto.getTelefono()));
        capturar(cambios, "direccion",    str(t.getDireccion()), str(dto.getDireccion()));
        capturar(cambios, "idPuesto",
                str(t.getPuesto() != null ? t.getPuesto().getIdPuesto() : null),
                str(dto.getIdPuesto()));
        capturar(cambios, "idGenero",
                str(t.getGenero() != null ? t.getGenero().getIdGenero() : null),
                str(dto.getIdGenero()));

        Puesto puesto = puestoRepository.findById(dto.getIdPuesto())
                .orElseThrow(() -> new ResourceNotFoundException("Puesto no encontrado"));
        Genero genero = generoRepository.findById(dto.getIdGenero())
                .orElseThrow(() -> new ResourceNotFoundException("Genero no encontrado"));

        // Cambiar de area deja al trabajador en un grupo que ya no le
        // corresponde, en contra de RN-20.
        boolean cambiaArea = t.getArea() != null && puesto.getArea() != null
                && !t.getArea().getIdArea().equals(puesto.getArea().getIdArea());
        if (cambiaArea && t.getGrupoTrabajo() != null) {
            t.setGrupoTrabajo(null);
        }

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

        StringBuilder credencial = new StringBuilder();
        if (esSuperAdmin && t.getUsuario() != null && emailCambio) {
            t.getUsuario().setUsername(dto.getEmail());
            credencial.append("La nueva credencial del trabajador es ")
                      .append(dto.getEmail()).append(".");
        }
        // El cambio de documento ya NO toca la contrasena: la condicion
        // de cambio obligatorio vive en debeCambiarPassword y no depende
        // del documento (RN-07).

        Trabajador guardado = trabajadorRepository.save(t);
        auditoriaService.registrarCambios(TABLA, guardado.getIdTrabajador(), cambios);

        TrabajadorResponseDTO resp = toDto(guardado);
        if (credencial.length() > 0) resp.setMensajeCredencial(credencial.toString());
        if (cambiaArea) resp.setMensajeCredencial(
                (resp.getMensajeCredencial() != null ? resp.getMensajeCredencial() + " " : "")
                + "El cambio de area retiro al trabajador de su grupo anterior.");
        return resp;
    }

    /** Autoedicion: solo telefono, direccion y contacto de emergencia. */
    private TrabajadorResponseDTO actualizarSoloContacto(Trabajador t, TrabajadorRequestDTO dto) {
        Map<String, String[]> cambios = new LinkedHashMap<>();
        capturar(cambios, "telefono",  str(t.getTelefono()),  str(dto.getTelefono()));
        capturar(cambios, "direccion", str(t.getDireccion()), str(dto.getDireccion()));
        capturar(cambios, "contactoEmergencias",
                str(t.getContactoEmergencias()), str(dto.getContactoEmergencias()));
        capturar(cambios, "nroContacto", str(t.getNroContacto()), str(dto.getNroContacto()));

        t.setTelefono(dto.getTelefono());
        t.setDireccion(dto.getDireccion());
        t.setContactoEmergencias(dto.getContactoEmergencias());
        t.setNroContacto(dto.getNroContacto());
        t.setParentesco(dto.getParentesco());

        Trabajador guardado = trabajadorRepository.save(t);
        auditoriaService.registrarCambios(TABLA, guardado.getIdTrabajador(), cambios);
        return toDto(guardado);
    }

    // ════════════════════════════════════════════════════════════
    // CONSULTAS
    // ════════════════════════════════════════════════════════════

    @Override
    public Page<TrabajadorResponseDTO> obtenerTodosLosTrabajadores(EstadoTrabajador estado,
                                                                   Pageable pageable) {
        return trabajadorRepository.findAllByEstado(estado, pageable).map(this::toDto);
    }

    @Override
    public TrabajadorResponseDTO obtenerTrabajadorById(Long id) {
        securityHelper.verificarAccesoPropioOAdmin(id);
        Trabajador t = trabajadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado: " + id));
        return toDto(t);
    }

    @Override
    public Page<TrabajadorResponseDTO> buscarTrabajadores(String q, EstadoTrabajador estado,
                                                          Pageable pageable) {
        if (securityHelper.esTrabajador()) {
            Long idPropio = securityHelper.getIdTrabajadorAutenticado();
            Trabajador t = trabajadorRepository.findById(idPropio)
                    .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado."));
            return new PageImpl<>(List.of(toDto(t)), pageable, 1);
        }
        return trabajadorRepository.buscarPorTermino(q, estado, pageable).map(this::toDto);
    }

    // ════════════════════════════════════════════════════════════
    // CESAR (CU09)
    // ════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void cesarTrabajador(Long id, String motivo, LocalDate fechaCese) {
        Trabajador t = trabajadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado: " + id));

        if (t.getEstado() == EstadoTrabajador.INACTIVO)
            throw new BusinessException("El trabajador ya se encuentra inactivo.");

        if (fechaCese.isAfter(LocalDate.now()))
            throw new BusinessException("La fecha de cese no puede ser una fecha futura.");

        periodoLaboralRepository.findPeriodoActivo(id).ifPresent(p -> {
            if (fechaCese.isBefore(p.getFechaIngreso()))
                throw new BusinessException(
                        "La fecha de cese no puede ser anterior a la fecha de ingreso ("
                                + p.getFechaIngreso() + ").");
        });

        // ---- Marcaciones posteriores (RN-10) ----
        // Faltaba por completo. Sin esta validacion, un cese con fecha
        // retroactiva dejaba jornadas registradas despues de que el
        // trabajador supuestamente ya no laboraba.
        long posteriores = asistenciaRepository.countMarcacionesPosterioresA(id, fechaCese);
        if (posteriores > 0)
            throw new BusinessException(
                    "No se puede cesar con fecha " + fechaCese + ": existen " + posteriores
                            + " marcacion(es) registradas despues de esa fecha.");

        t.setEstado(EstadoTrabajador.INACTIVO);
        if (t.getUsuario() != null) t.getUsuario().setEnabled(false);

        // Al cesar, se libera el grupo para no dejar inactivos ocupando
        // cupo en la programacion semanal.
        t.setGrupoTrabajo(null);

        MotivoCese motivoCese = resolverMotivoCese(motivo);

        periodoLaboralRepository.findPeriodoActivo(id).ifPresent(p -> {
            p.setFechaCese(fechaCese);
            p.setMotivoCese(motivoCese);
            if (motivoCese == null) p.setDetalleMotivoCese(motivo);
            periodoLaboralRepository.save(p);
        });

        historialPuestoRepository.findPuestoActivo(id).ifPresent(h -> {
            h.setFechaFin(fechaCese);
            h.setMotivoCambio("Cese del trabajador: " + motivo);
            historialPuestoRepository.save(h);
        });

        trabajadorRepository.save(t);

        // El motivo va en su propio campo, no concatenado dentro del
        // valor nuevo como hacia el prototipo.
        auditoriaService.registrarConMotivo(TABLA, id, "CESAR",
                motivo + " | Fecha de cese: " + fechaCese);
    }

    // ════════════════════════════════════════════════════════════
    // REINGRESAR (CU10)
    // ════════════════════════════════════════════════════════════

    /**
     * El puesto es OPCIONAL. Si no se envia, se conserva el del registro
     * anterior (RN-12). El prototipo lo exigia siempre, en contradiccion
     * con la regla.
     */
    @Override
    @Transactional
    public TrabajadorResponseDTO reingresarTrabajador(Long id, Integer idPuesto) {
        Trabajador t = trabajadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado: " + id));

        if (t.getEstado() == EstadoTrabajador.ACTIVO)
            throw new BusinessException("El trabajador ya se encuentra activo.");

        Puesto puesto = (idPuesto != null)
                ? puestoRepository.findById(idPuesto)
                        .orElseThrow(() -> new ResourceNotFoundException("Puesto no encontrado"))
                : t.getPuesto();

        if (puesto == null)
            throw new BusinessException(
                    "El trabajador no tiene un puesto anterior registrado. Debes indicar uno.");

        t.setEstado(EstadoTrabajador.ACTIVO);
        t.setPuesto(puesto);

        if (t.getUsuario() != null) {
            t.getUsuario().setEnabled(true);
            t.getUsuario().setPassword(passwordEncoder.encode(t.getNroDocumento()));
            t.getUsuario().setDebeCambiarPassword(true);
            t.getUsuario().setRol("ROLE_TRABAJADOR");   // vuelve al minimo (RN-03)
        }

        // Nuevo carne: invalida el anterior (CU11)
        asignarCodigoBarras(t);

        periodoLaboralRepository.save(PeriodoLaboral.builder()
                .trabajador(t).fechaIngreso(LocalDate.now()).build());
        historialPuestoRepository.save(HistorialPuesto.builder()
                .trabajador(t).puesto(puesto)
                .fechaInicio(LocalDate.now()).motivoCambio("Reingreso a la empresa").build());

        Trabajador guardado = trabajadorRepository.save(t);

        auditoriaService.registrarCampo(TABLA, id, "REINGRESAR", "idPuesto",
                null, String.valueOf(puesto.getIdPuesto())
                        + (idPuesto == null ? " (conservado del registro anterior)" : ""));

        return toDto(guardado);
    }

    // ════════════════════════════════════════════════════════════
    // RESTABLECER CONTRASENA (CU02)
    // ════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void resetearPassword(Long id) {
        Trabajador t = trabajadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado: " + id));

        if (t.getUsuario() == null)
            throw new BusinessException("Este trabajador no tiene cuenta de acceso.");

        if (!t.isActivo())
            throw new BusinessException(
                    "No se puede restablecer la contrasena de un trabajador inactivo.");

        t.getUsuario().setPassword(passwordEncoder.encode(t.getNroDocumento()));
        // Bloquea cualquier otra accion hasta que la cambie (RN-07)
        t.getUsuario().setDebeCambiarPassword(true);

        trabajadorRepository.save(t);
        auditoriaService.registrar(TABLA, id, "RESET_PASSWORD");
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════

    /**
     * Genera el codigo de barras del carne (CU11, RT-02).
     *
     * Un unico codigo por trabajador, sin sufijos de entrada ni salida.
     * Al reponer el carne se sobrescribe, lo que invalida el anterior.
     *
     * El valor es el identificador del trabajador, segun lo acordado.
     * Cambiarlo por un valor aleatorio no adivinable el dia de manana es
     * reemplazar este metodo: el algoritmo de marcacion consulta por
     * codigo y no conoce su forma.
     */
    private void asignarCodigoBarras(Trabajador t) {
        t.setCodigoBarras(String.valueOf(t.getIdTrabajador()));
        t.setFechaGeneracionCarnet(LocalDateTime.now());
    }

    /** Busca el motivo en el catalogo. Null si es un texto libre ("Otro"). */
    private MotivoCese resolverMotivoCese(String motivo) {
        if (motivo == null || motivo.isBlank())
            throw new BusinessException("El motivo de cese es obligatorio.");
        return motivoCeseRepository.findByActivoTrueOrderByNombreAsc().stream()
                .filter(m -> m.getNombre().equalsIgnoreCase(motivo.trim()))
                .findFirst().orElse(null);
    }

    private TrabajadorResponseDTO toDto(Trabajador t) {
        TrabajadorResponseDTO dto = trabajadorMapper.toDto(t);
        if (t.getUsuario() != null) dto.setRol(t.getUsuario().getRol());
        // El grupo se lee directo de la entidad: la pertenencia vive en
        // Trabajador.grupoTrabajo y ya no requiere consultar el
        // repositorio de grupos.
        if (t.getGrupoTrabajo() != null) {
            dto.setGrupoActualId(t.getGrupoTrabajo().getIdGrupo());
            dto.setGrupoActualNombre(t.getGrupoTrabajo().getNombre());
        }
        return dto;
    }

    private void capturar(Map<String, String[]> mapa, String campo,
                          String anterior, String nuevo) {
        mapa.put(campo, new String[]{ anterior, nuevo });
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }

    private void validarFormatoDocumento(String tipo, String numero) {
        switch (tipo) {
            case "DNI" -> {
                if (!numero.matches("^\\d{8}$"))
                    throw new BusinessException("El DNI debe tener exactamente 8 digitos numericos.");
            }
            case "CE" -> {
                if (!numero.matches("^\\d{9,12}$"))
                    throw new BusinessException(
                            "El Carnet de Extranjeria debe tener entre 9 y 12 digitos.");
            }
            case "PASAPORTE" -> {
                if (!numero.matches("^[a-zA-Z0-9]{6,20}$"))
                    throw new BusinessException(
                            "El Pasaporte debe tener entre 6 y 20 caracteres alfanumericos.");
            }
            default -> throw new BusinessException("Tipo de documento no valido.");
        }
    }
}
