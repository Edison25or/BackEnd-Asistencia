package com.idat.asistencia.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UsuarioInfoDTO {
    private String  nombre;               // Nombre completo del trabajador
    private String  rol;                  // Ej: "ROLE_SUPERADMIN"
    private String  email;                // username (= email del trabajador)
    private boolean debeCambiarPassword;  // true cuando la clave es igual al DNI
}