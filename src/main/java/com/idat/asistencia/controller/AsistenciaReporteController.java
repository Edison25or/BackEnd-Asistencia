package com.idat.asistencia.controller;

import com.idat.asistencia.dto.AsistenciaReporteDTO;
import com.idat.asistencia.service.AsistenciaReporteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/asistencias/reporte")
@RequiredArgsConstructor
public class AsistenciaReporteController {

    private final AsistenciaReporteService service;

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE','SUPERVISOR','TRABAJADOR')")
    @GetMapping
    public ResponseEntity<List<AsistenciaReporteDTO>> getReporte(
            @RequestParam String  fechaInicio,
            @RequestParam String  fechaFin,
            @RequestParam(required = false) Long    idTrabajador,
            @RequestParam(required = false) Integer idArea) {

        return ResponseEntity.ok(
                service.getReporte(fechaInicio, fechaFin, idTrabajador, idArea)
        );
    }
}