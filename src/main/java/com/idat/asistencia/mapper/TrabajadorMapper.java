package com.idat.asistencia.mapper;

import com.idat.asistencia.dto.TrabajadorRequestDTO;
import com.idat.asistencia.dto.TrabajadorResponseDTO;
import com.idat.asistencia.model.entity.Trabajador;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TrabajadorMapper {

    // ==========================================================
    // 1. DE DTO A ENTIDAD (Lo que llega de Postman para guardar)
    // ==========================================================
    @Mapping(source = "idGenero", target = "genero.idGenero")
    @Mapping(source = "idPuesto", target = "puesto.idPuesto")
    @Mapping(target = "idTrabajador", ignore = true)
    @Mapping(target = "usuario", ignore = true) // Ignoramos esto porque lo creamos en el Service
    @Mapping(source = "PNombre", target = "pNombre")
    @Mapping(source = "SNombre", target = "sNombre")
    @Mapping(source = "APaterno", target = "aPaterno")
    @Mapping(source = "AMaterno", target = "aMaterno")
    @Mapping(target = "estado", ignore = true)
    Trabajador toEntity(TrabajadorRequestDTO dto);

    // ==========================================================
    // 2. DE ENTIDAD A DTO (Lo que devolvemos a Postman/Android)
    // ==========================================================

    // Concatenamos con comillas dobles escapadas para evitar errores de char en Java

    @Mapping(target = "nombreCompleto",
            expression = "java(trabajador.getPNombre() + \" \" + trabajador.getAPaterno() + \" \" + trabajador.getAMaterno())")
    // Nombres individuales
    @Mapping(source = "PNombre",  target = "pNombre")
    @Mapping(source = "SNombre",  target = "sNombre")
    @Mapping(source = "APaterno", target = "aPaterno")
    @Mapping(source = "AMaterno", target = "aMaterno")
    // Puesto y área: nombre e ID
    @Mapping(source = "puesto.puesto",       target = "puestoNombre")
    @Mapping(source = "puesto.idPuesto",     target = "idPuesto")
    @Mapping(source = "puesto.area.area",    target = "areaNombre")
    @Mapping(source = "puesto.area.idArea",  target = "idArea")
    // Género: nombre e ID
    @Mapping(source = "genero.genero",       target = "generoNombre")
    @Mapping(source = "genero.idGenero",     target = "idGenero")
    // Rol desde usuario
    @Mapping(source = "usuario.rol",         target = "rol")
    // Resto de campos
    @Mapping(source = "docIdentidad",        target = "docIdentidad")
    @Mapping(source = "nroDocumento",        target = "nroDocumento")
    @Mapping(source = "fechaNac",            target = "fechaNac")
    @Mapping(source = "email",               target = "email")
    @Mapping(source = "telefono",            target = "telefono")
    @Mapping(source = "direccion",           target = "direccion")
    @Mapping(source = "contactoEmergencias", target = "contactoEmergencias")
    @Mapping(source = "nroContacto",         target = "nroContacto")
    @Mapping(source = "parentesco",          target = "parentesco")
    @Mapping(source = "estado",              target = "estado")
    @Mapping(target = "grupoActualId",       ignore = true)
    @Mapping(target = "grupoActualNombre",   ignore = true)
    TrabajadorResponseDTO toDto(Trabajador trabajador);

}