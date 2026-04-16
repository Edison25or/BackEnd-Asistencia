package com.idat.asistencia.dto;

import com.idat.asistencia.model.enums.Parentesco;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TrabajadorRequestDTO {

    @NotBlank(message = "El tipo de documento es obligatorio")
    @Pattern(regexp = "DNI|CE|PASAPORTE", message = "Tipo de documento inválido (DNI, CE o PASAPORTE)")
    private String docIdentidad;

    @NotBlank(message = "El número de documento es obligatorio")
    @Size(min = 8, max = 20, message = "El número de documento debe tener entre 8 y 20 caracteres")
    private String nroDocumento;

    @NotBlank(message = "El primer nombre es obligatorio")
    @Size(max = 50, message = "El primer nombre no puede exceder 50 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ ]+$", message = "El primer nombre solo debe contener letras")
    private String pNombre;

    @Size(max = 50, message = "El segundo nombre no puede exceder 50 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ ]*$", message = "El segundo nombre solo debe contener letras")
    private String sNombre;

    @NotBlank(message = "El apellido paterno es obligatorio")
    @Size(max = 50, message = "El apellido paterno no puede exceder 50 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ ]+$", message = "El apellido paterno solo debe contener letras")
    private String aPaterno;

    @NotBlank(message = "El apellido materno es obligatorio")
    @Size(max = 50, message = "El apellido materno no puede exceder 50 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ ]+$", message = "El apellido materno solo debe contener letras")
    private String aMaterno;

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    @Past(message = "La fecha de nacimiento debe ser una fecha pasada")
    private LocalDate fechaNac;

    @Size(max = 255, message = "La dirección no puede exceder 255 caracteres")
    private String direccion;

    @Pattern(regexp = "^$|^9\\d{8}$", message = "El teléfono debe ser un celular peruano válido (9 dígitos empezando con 9)")
    private String telefono;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato del correo no es válido")
    @Size(max = 100, message = "El correo no puede exceder 100 caracteres")
    private String email;

    @Size(max = 100, message = "El nombre del contacto no puede exceder 100 caracteres")
    private String contactoEmergencias;

    @Pattern(regexp = "^$|^9\\d{8}$", message = "El número de contacto debe ser un celular válido (9 dígitos empezando con 9)")
    private String nroContacto;

    private Parentesco parentesco;

    @NotNull(message = "El ID del puesto es obligatorio")
    private Integer idPuesto;

    @NotNull(message = "El ID del género es obligatorio")
    private Integer idGenero;
}