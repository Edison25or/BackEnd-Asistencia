package com.idat.asistencia.service;

import com.idat.asistencia.dto.AsistenciaReporteDTO;
import java.util.List;

public interface AsistenciaReporteService {
    List<AsistenciaReporteDTO> getReporte(
            String fechaInicio,
            String fechaFin,
            Long   idTrabajador,
            Integer idArea
    );
}
