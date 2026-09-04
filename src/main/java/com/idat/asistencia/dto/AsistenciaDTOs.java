package com.idat.asistencia.dto;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

public class AsistenciaDTOs {

    /**
     * Respuesta del lector.
     *
     * accion puede ser ENTRADA, SALIDA, CONFIRMACION_REQUERIDA o
     * IGNORADO. Se retira estadoDiario, que era un String libre con una
     * nomenclatura distinta de las otras dos que convivian en el sistema;
     * ahora la clasificacion la lleva tipo, sobre el enum unico
     * TipoRegistro.
     */
    @Data @Builder
    public static class MarcarAsistenciaResponse {
        private Long    idTrabajador;
        private String  nombreCompleto;
        private String  accion;
        private String  hora;
        private String  estado;
        private String  tipo;
        private String  puestoNombre;
        private String  turnoNombre;
        private String  ingresoProg;
        private String  salidaProg;
        private Integer minTardanza;
        private boolean requiereRevision;

        /** true cuando el lector espera el segundo escaneo (HU-22). */
        private boolean requiereConfirmacion;
        private Integer segundosParaConfirmar;

        /** Mensaje listo para mostrar en el dispositivo (RNF009). */
        private String  mensaje;
    }

    @Data @Builder
    public static class AsistenciaResumenDTO {
        private Long    idAsistencia;
        private Long    idTrabajador;
        private String  nombreCompleto;
        private String  nroDocumento;
        private String  puestoNombre;
        private String  areaNombre;
        private String  fecha;
        private String  horaEntrada;
        private String  horaSalida;
        private String  estado;
        private String  tipo;
        private String  turnoNombre;
        private boolean requiereRevision;
        private Integer minTardanza;

        /**
         * Ausencia que cubre la jornada, si la hay.
         *
         * Sin este dato, el reporte no puede distinguir una falta
         * injustificada de un dia cubierto por permiso: ambas llegan sin
         * marcacion y con el mismo estado.
         */
        private String permisoAsociado;
        private String faltaJustificadaAsociada;

        /** Minutos efectivamente trabajados, para los totales del reporte. */
        private Integer minHorasTotales;
        private Integer minutosFeriado;
        private boolean esDiaNoLaborable;
    }

    /** Version publica para el kiosco. No expone identificadores. */
    @Data @Builder
    public static class EnPlantaPublicDTO {
        private String nombreCompleto;
        private String puestoNombre;
        private String areaNombre;
        private String horaEntrada;
        private String turnoNombre;
    }

    @Data @Builder
    public static class AsistenciaRevisionDTO {
        private Long    idAsistencia;
        private Long    idTrabajador;
        private String  nombreCompleto;
        private String  nroDocumento;
        private String  puestoNombre;
        private String  areaNombre;
        private String  fecha;
        private String  tipo;
        private String  estado;
        private boolean requiereRevision;
        private String  turnoNombre;
        private boolean esDiaNoLaborable;

        private String  ingresoProg;
        private String  salidaProg;
        private Integer minRefrigerioProg;
        private Integer minNetosProg;
        private Integer minExtraProg;

        private String  ingresoReal;
        private String  salidaReal;

        private Integer minPrevIngProg;
        private Integer minPostSalProg;
        private Integer minTardanza;
        private Integer minSalTemprana;
        private Integer minHorasTotales;
        private Integer minutosFeriado;

        private Integer valMinPrevIng;
        private Integer valMinPostSal;
        private String  resultadoValidacion;

        private String  permisoAsociado;
        private String  faltaJustificadaAsociada;

        private String  revisadoPor;
        private String  revisadoEn;
        private String  observacion;

        private String  colorPrev;
        private String  colorPost;
    }

    /** Validacion de hora extra excepcional (CU18). */
    @Data
    public static class ValidarTiemposRequest {
        @NotNull(message = "El ID de asistencia es obligatorio")
        private Long    idAsistencia;

        @Min(value = 0, message = "Los minutos no pueden ser negativos")
        @Max(value = 720, message = "Los minutos no pueden superar las 12 horas")
        private Integer valMinPrevIng;

        @Min(value = 0, message = "Los minutos no pueden ser negativos")
        @Max(value = 720, message = "Los minutos no pueden superar las 12 horas")
        private Integer valMinPostSal;

        /** Obligatorio (RN-02). El prototipo no lo exigia realmente. */
        @NotBlank(message = "El motivo o comentario es obligatorio")
        @Size(max = 500, message = "La observacion no puede exceder 500 caracteres")
        private String  observacion;

        @Pattern(regexp = "^$|^(APROBADO|RECHAZADO)$",
                 message = "El resultado debe ser APROBADO o RECHAZADO")
        private String  resultado;

        /** Turno a asignar cuando la jornada no tiene esquema (RN-25). */
        private Integer idTurno;
    }

    /** Correccion de marcacion faltante o incompleta (CU15). */
    @Data
    public static class CorregirMarcacionRequest {
        @NotNull(message = "El ID de asistencia es obligatorio")
        private Long   idAsistencia;

        /** Formato ISO completo: "2026-03-27T22:00:00". */
        private String ingresoReal;
        private String salidaReal;

        @NotBlank(message = "El motivo es obligatorio")
        @Size(min = 5, max = 500, message = "El motivo debe tener entre 5 y 500 caracteres")
        private String motivo;
    }

    /**
     * Registro manual por contingencia (CU19).
     *
     * Ingreso y salida son OPCIONALES por separado: la contingencia con
     * dato parcial conocido es el caso mas frecuente. El prototipo los
     * exigia ambos de forma simultanea.
     */
    @Data
    public static class RegistrarNoProgramadaRequest {
        @NotNull(message = "El ID del trabajador es obligatorio")
        private Long   idTrabajador;

        @NotBlank(message = "La fecha es obligatoria")
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$",
                 message = "La fecha debe tener formato yyyy-MM-dd")
        private String fecha;

        @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$",
                 message = "La hora de ingreso debe tener formato HH:mm")
        private String ingresoReal;

        @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$",
                 message = "La hora de salida debe tener formato HH:mm")
        private String salidaReal;

        private Integer idTurno;

        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 500, message = "La observacion no puede exceder 500 caracteres")
        private String observacion;
    }

    @Data @Builder
    public static class QuincenaResumenDTO {
        private Long   idQuincena;
        private String descripcion;
        private String inicio;
        private String fin;
        private String estado;
        /** Registros que impiden cerrar la quincena (RN-37). */
        private long   bloqueantes;
    }
}
