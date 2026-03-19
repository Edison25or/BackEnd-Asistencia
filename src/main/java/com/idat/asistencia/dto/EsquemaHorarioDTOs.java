package com.idat.asistencia.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.util.List;

public class EsquemaHorarioDTOs {

    // ── REQUEST: crear nuevo esquema (versión 1) ─────────────
    @Data
    public static class EsquemaRequest {
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 80)
        private String nombre;

        @Size(max = 200)
        private String descripcion;

        @Min(0) @Max(60)
        private Integer toleranciaMinutos = 10;

        @NotNull @Valid
        private List<HorarioDiaRequest> horariosDia;
    }

    // ── REQUEST: crear nueva versión de un esquema existente ──
    @Data
    public static class NuevaVersionRequest {
        @NotBlank(message = "La fecha de vigencia es obligatoria")
        private String vigenteDesde;   // "yyyy-MM-dd"

        @Size(max = 200)
        private String descripcion;

        @Min(0) @Max(60)
        private Integer toleranciaMinutos;

        @NotNull @Valid
        private List<HorarioDiaRequest> horariosDia;
    }

    @Data
    public static class HorarioDiaRequest {
        @NotNull @Min(1) @Max(7)
        private Integer diaSemana;

        @NotNull
        private Boolean esDescanso;

        private String  horaEntrada;
        private Integer minutosRefrigerio;
        private Integer minutosNetos;
        private Integer minutosExtraProgramado;
    }

    // ── RESPONSE: un esquema con sus días ─────────────────────
    @Data @Builder
    public static class EsquemaResponse {
        private Integer idEsquema;
        private String  nombre;
        private String  grupoNombre;
        private String  descripcion;
        private Integer toleranciaMinutos;
        private Integer version;
        private String  vigenteDesde;
        private String  vigenteHasta;    // null = activo
        private boolean activo;
        private boolean vigente;         // true si vigenteHasta == null
        private List<HorarioDiaResponse> horariosDia;
        private String  totalHorasNetas;
        private String  totalHorasExtra;
        private String  totalHorasBrutas;
        // Indica si tiene programaciones — para el modal de advertencia
        private boolean tieneProgramaciones;
    }

    // ── RESPONSE: resumen de todas las versiones de un grupo ──
    @Data @Builder
    public static class EsquemaGrupoResponse {
        private String             grupoNombre;
        private List<EsquemaResponse> versiones;  // ordenadas desc por version
        private EsquemaResponse    versionActiva; // la de vigenteHasta == null
    }

    @Data @Builder
    public static class HorarioDiaResponse {
        private Long    idHorarioDia;
        private Integer diaSemana;
        private String  nombreDia;
        private Boolean esDescanso;
        private String  horaEntrada;
        private Integer minutosRefrigerio;
        private Integer minutosNetos;
        private Integer minutosExtraProgramado;
        private String  horaSalida;
        private String  horasNetasFormato;
        private String  extraFormato;
    }
}