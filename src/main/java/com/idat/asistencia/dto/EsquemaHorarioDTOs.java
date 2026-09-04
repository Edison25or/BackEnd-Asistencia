package com.idat.asistencia.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.util.List;

public class EsquemaHorarioDTOs {

    /**
     * Alta de esquema.
     *
     * toleranciaMinutos se desdobla en TRES tolerancias (RN-17): tardanza,
     * previa y posterior. Con una sola no se podia distinguir una entrada
     * anticipada normal de una que dispara la confirmacion por doble
     * escaneo.
     *
     * idTurno es obligatorio (RN-18).
     */
    @Data
    public static class EsquemaRequest {
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 80)
        private String  nombre;

        @Size(max = 200)
        private String  descripcion;

        @NotNull(message = "El turno es obligatorio")
        private Integer idTurno;

        @Min(0) @Max(120)
        private Integer toleranciaTardanza = 10;

        @Min(0) @Max(120)
        private Integer toleranciaPrevia = 15;

        @Min(0) @Max(120)
        private Integer toleranciaPosterior = 15;

        @NotNull @Valid
        @Size(min = 7, max = 7, message = "Debe enviar exactamente 7 dias")
        private List<HorarioDiaRequest> horariosDia;
    }

    @Data
    public static class NuevaVersionRequest {
        @NotBlank(message = "La fecha de vigencia es obligatoria")
        private String  vigenteDesde;

        @Size(max = 200)
        private String  descripcion;

        private Integer idTurno;
        @Min(0) @Max(120) private Integer toleranciaTardanza;
        @Min(0) @Max(120) private Integer toleranciaPrevia;
        @Min(0) @Max(120) private Integer toleranciaPosterior;

        @NotNull @Valid
        @Size(min = 7, max = 7, message = "Debe enviar exactamente 7 dias")
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

    @Data @Builder
    public static class EsquemaResponse {
        private Integer idEsquema;
        private String  nombre;
        private String  grupoNombre;
        private String  descripcion;

        private Integer idTurno;
        private String  turnoNombre;
        /** true si el turno cruza la medianoche. */
        private boolean turnoCruzaMedianoche;

        private Integer toleranciaTardanza;
        private Integer toleranciaPrevia;
        private Integer toleranciaPosterior;

        private Integer version;
        private String  vigenteDesde;
        private String  vigenteHasta;
        private boolean activo;
        private boolean vigente;

        private List<HorarioDiaResponse> horariosDia;
        private String  totalHorasNetas;
        private String  totalHorasExtra;
        private String  totalHorasBrutas;
        private boolean tieneProgramaciones;
    }

    @Data @Builder
    public static class EsquemaGrupoResponse {
        private String                grupoNombre;
        private List<EsquemaResponse> versiones;
        private EsquemaResponse       versionActiva;
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
        /** true si la salida cae en el dia calendario siguiente. */
        private boolean salidaDiaSiguiente;
        private String  horasNetasFormato;
        private String  extraFormato;
    }
}
