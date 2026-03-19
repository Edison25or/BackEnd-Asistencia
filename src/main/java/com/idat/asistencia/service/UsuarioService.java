package com.idat.asistencia.service;

import com.idat.asistencia.dto.UsuarioInfoDTO;

public interface UsuarioService {
    String cambiarRol(Integer idTrabajador, String nuevoRol);
    UsuarioInfoDTO getMiInfo(String username);
    void cambiarPassword(String username, String passwordActual, String passwordNueva);
}