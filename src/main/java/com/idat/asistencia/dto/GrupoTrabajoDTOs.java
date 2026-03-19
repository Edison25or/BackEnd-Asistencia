package com.idat.asistencia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.util.List;

public class GrupoTrabajoDTOs {

    @Data
    public static class GrupoRequest {
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 50)
        private String nombre;

        @Size(max = 150)
        private String descripcion;

        // Puede ser null o vacío (grupos vacíos permitidos)
        private List<Long> idsTrabajadores;
    }

    @Data
    @Builder
    public static class GrupoResponse {
        private Integer idGrupo;
        private String  nombre;
        private String  descripcion;
        private Integer totalTrabajadores;
        private List<TrabajadorResumenDTO> trabajadores;
    }

    @Data
    @Builder
    public static class TrabajadorResumenDTO {
        private Long   idTrabajador;
        private String nombreCompleto;
        private String nroDocumento;
        private String puestoNombre;
        private String areaNombre;
        // Indica si este trabajador ya pertenece a otro grupo (informativo para el frontend)
        private String grupoActual;
    }
}