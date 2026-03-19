package com.idat.asistencia.dto;

public class AuthDTOs {
    public record LoginRequest(String username, String password) {}
    public record AuthResponse(String token) {}
    public record RecuperarPasswordRequest(String email) {}
    public record CambiarPasswordRequest(String passwordActual, String passwordNueva) {}
}