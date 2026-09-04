package com.idat.asistencia.controller;

import com.idat.asistencia.dto.AsistenciaDTOs.*;
import com.idat.asistencia.service.AsistenciaService;
import com.idat.asistencia.service.CierreDiarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/asistencia")
@RequiredArgsConstructor
public class AsistenciaController {

    private final AsistenciaService   service;
    private final CierreDiarioService cierreDiarioService;

    // ---------- Lector de planta ----------
    // Sin sesion de usuario, por tratarse del dispositivo fisico.
    // Restringido por IP en SecurityConfig (RS-05).

    @PostMapping("/marcar")
    public ResponseEntity<MarcarAsistenciaResponse> marcar(@RequestParam String codigo) {
        return ResponseEntity.ok(service.marcar(codigo));
    }

    @GetMapping("/en-planta-publica")
    public ResponseEntity<List<EnPlantaPublicDTO>> enPlantaPublica() {
        return ResponseEntity.ok(service.getEnPlantaPublica());
    }

    // ---------- Panel del dia ----------

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE','SUPERVISOR')")
    @GetMapping("/en-planta")
    public ResponseEntity<List<AsistenciaResumenDTO>> enPlanta() {
        return ResponseEntity.ok(service.getTrabajadoresEnPlanta());
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE','SUPERVISOR')")
    @GetMapping("/dia")
    public ResponseEntity<List<AsistenciaResumenDTO>> dia() {
        return ResponseEntity.ok(service.getAsistenciasDia());
    }

    // ---------- Bandeja de pendientes ----------

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE')")
    @GetMapping("/revision/{idQuincena}")
    public ResponseEntity<List<AsistenciaRevisionDTO>> revision(@PathVariable Long idQuincena) {
        return ResponseEntity.ok(service.getParaRevision(idQuincena));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE')")
    @PatchMapping("/validar-tiempos")
    public ResponseEntity<AsistenciaRevisionDTO> validar(
            @Valid @RequestBody ValidarTiemposRequest req, Authentication auth) {
        return ResponseEntity.ok(service.validarTiempos(req, auth.getName()));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE')")
    @PatchMapping("/corregir-marcacion")
    public ResponseEntity<AsistenciaRevisionDTO> corregir(
            @Valid @RequestBody CorregirMarcacionRequest req) {
        return ResponseEntity.ok(service.corregirMarcacion(req));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @PostMapping("/contingencia")
    public ResponseEntity<AsistenciaRevisionDTO> registrarContingencia(
            @Valid @RequestBody RegistrarNoProgramadaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.registrarNoProgramada(req));
    }

    // ---------- Quincenas ----------
    // El endpoint POST /quincenas desaparece: la quincena se autogenera
    // al confirmar la programacion semanal (RN-35, CU14).

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','JEFE','SUPERVISOR')")
    @GetMapping("/quincenas")
    public ResponseEntity<List<QuincenaResumenDTO>> quincenas() {
        return ResponseEntity.ok(service.getQuincenas());
    }

    // ---------- Cierre diario (CU29) ----------

    /** Ejecucion manual del proceso, para pruebas y regularizaciones. */
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @PostMapping("/cierre-diario")
    public ResponseEntity<CierreDiarioService.ResultadoCierre> ejecutarCierreDiario() {
        return ResponseEntity.ok(cierreDiarioService.ejecutar());
    }
}
