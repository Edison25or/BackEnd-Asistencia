package com.idat.asistencia.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AsistenciaReporteDTO {
    private Long   idTrabajador;
    private String nombreCompleto;
    private String nroDocumento;
    private String areaNombre;
    private String puestoNombre;
    private String fecha;           // "yyyy-MM-dd"
    private String diaSemana;       // "Lunes", "Martes"...
    private String horaEntrada;     // "HH:mm" o null
    private String horaSalida;      // "HH:mm" o null
    private String estado;          // "A_TIEMPO", "TARDE", "FALTA", "JUSTIFICADO"
    private String estadoLabel;     // "A tiempo", "Tardanza", "Falta", "Justificado"
    private String observacion;
    private Long   minutosLaborados; // diferencia entrada-salida en minutos
}

