package com.idat.asistencia.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UsuarioInfoDTO {
    private Integer idUsuario;
    private String  username;
    private String  rol;
    private Long    idTrabajador;
    private String  nombreCompleto;
    private String  puestoNombre;
    private String  areaNombre;

    /**
     * Campo persistido, no inferido comparando el hash contra el numero
     * de documento como hacia el prototipo (RN-07).
     */
    private boolean debeCambiarPassword;

    // Alias conservados para no romper el frontend existente
    public String getNombre() { return nombreCompleto; }
    public String getEmail()  { return username; }
}
