package com.idat.asistencia.controller;

import com.idat.asistencia.dto.DashboardDTOs.EstadisticasResponse;
import com.idat.asistencia.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Estadisticas para la toma de decisiones.
 *
 * No se expone al rol TRABAJADOR: son datos agregados de todo el
 * personal, y un trabajador solo puede ver sus propios registros (RN-06).
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE','SUPERVISOR')")
    @GetMapping("/estadisticas")
    public ResponseEntity<EstadisticasResponse> estadisticas(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer idArea) {

        return ResponseEntity.ok(service.calcular(desde, hasta, idArea));
    }
}
