package com.idat.asistencia.dto;

import com.idat.asistencia.model.enums.EstadoTrabajador;
import com.idat.asistencia.model.enums.Parentesco;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class TrabajadorResponseDTO {
    // Identificación
    private Long idTrabajador;
    private String docIdentidad;
    private String nroDocumento;

    // Nombres (para mostrar y editar)
    private String nombreCompleto;
    private String pNombre;
    private String sNombre;
    private String aPaterno;
    private String aMaterno;

    // Datos personales
    private LocalDate fechaNac;
    private String generoNombre;
    private Integer idGenero;

    // Contacto
    private String email;
    private String telefono;
    private String direccion;

    // Contacto de emergencia
    private String contactoEmergencias;
    private String nroContacto;
    private Parentesco parentesco;

    // Puesto y área (nombre para mostrar + ID para editar)
    private String puestoNombre;
    private Integer idPuesto;
    private String areaNombre;
    private Integer idArea;

    // Sistema
    private String rol;
    private EstadoTrabajador estado;

    // Grupo al que pertenece actualmente (null si no tiene)
    private Integer grupoActualId;
    private String  grupoActualNombre;

    // Mensaje informativo sobre cambio de credenciales (email/DNI)
    private String mensajeCredencial;
}