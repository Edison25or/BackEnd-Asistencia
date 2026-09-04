package com.idat.asistencia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.util.List;

public class GrupoTrabajoDTOs {

    @Data
    public static class GrupoRequest {
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 50)
        private String  nombre;

        @Size(max = 150)
        private String  descripcion;

        /** Obligatoria: un grupo es de una sola area (RN-20). */
        @NotNull(message = "El area es obligatoria")
        private Integer idArea;

        /** Puede ser null o vacia: los grupos vacios estan permitidos. */
        private List<Long> idsTrabajadores;
    }

    @Data @Builder
    public static class GrupoResponse {
        private Integer idGrupo;
        private String  nombre;
        private String  descripcion;
        private Integer idArea;
        private String  areaNombre;
        private Integer totalTrabajadores;
        private List<TrabajadorResumenDTO> trabajadores;
    }

    @Data @Builder
    public static class TrabajadorResumenDTO {
        private Long   idTrabajador;
        private String nombreCompleto;
        private String nroDocumento;
        private String puestoNombre;
        private String areaNombre;
        /** Grupo al que ya pertenece, si lo hay. Informativo. */
        private String grupoActual;
    }
}
