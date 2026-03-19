package com.idat.asistencia.service;

import com.idat.asistencia.dto.EsquemaHorarioDTOs.*;
import java.util.List;

public interface EsquemaHorarioService {
    // Para dropdowns (solo versiones vigentes y activas)
    List<EsquemaResponse> getAll();

    // Para la pantalla de gestión (agrupa por grupoNombre con todas las versiones)
    List<EsquemaGrupoResponse> getAllAgrupados();

    EsquemaResponse getById(Integer id);

    // Crea un nuevo esquema (versión 1)
    EsquemaResponse crear(EsquemaRequest request);

    // Crea una nueva versión de un esquema existente y cierra la anterior
    EsquemaResponse crearNuevaVersion(String grupoNombre, NuevaVersionRequest request);

    // Toggle activo/inactivo (reemplaza a eliminar)
    EsquemaResponse toggleActivo(Integer id);

    // Cuántas programaciones tiene este esquema (para modal de advertencia)
    long contarProgramaciones(Integer idEsquema);
}