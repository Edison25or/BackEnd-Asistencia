package com.idat.asistencia.dto;

import com.idat.asistencia.model.enums.Parentesco;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data // Genera getters, setters, toString, etc.
@Builder // Mantiene tu funcionalidad de Builder
@AllArgsConstructor // Genera constructor con todos los campos
@NoArgsConstructor // ¡ESTA ES LA QUE FALTA! Genera el constructor vacío para Jackson
public class TrabajadorRequestDTO {

    @NotBlank(message = "El tipo de documento es obligatorio")
    private String docIdentidad;

    @NotBlank(message = "El número de documento es obligatorio")
    private String nroDocumento;

    @NotBlank(message = "El primer nombre es obligatorio")
    private String pNombre;

    private String sNombre;

    @NotBlank(message = "El apellido paterno es obligatorio")
    private String aPaterno;

    @NotBlank(message = "El apellido materno es obligatorio")
    private String aMaterno;

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    private LocalDate fechaNac;

    private String direccion;
    private String telefono;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato del correo no es válido")
    private String email;
    private String contactoEmergencias;
    private String nroContacto;
    private Parentesco parentesco;

    @NotNull(message = "El ID del puesto es obligatorio")
    private Integer idPuesto;

    @NotNull(message = "El ID del género es obligatorio")
    private Integer idGenero;
}


