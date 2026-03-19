package com.idat.asistencia.controller;

import com.idat.asistencia.dto.MaestroDTOs.*;
import com.idat.asistencia.exception.BusinessException;
import com.idat.asistencia.exception.ResourceNotFoundException;
import com.idat.asistencia.model.entity.Area;
import com.idat.asistencia.model.entity.Genero;
import com.idat.asistencia.model.entity.Puesto;
import com.idat.asistencia.repository.AreaRepository;
import com.idat.asistencia.repository.GeneroRepository;
import com.idat.asistencia.repository.PuestoRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/maestros")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MaestroController {

    private final GeneroRepository generoRepository;
    private final AreaRepository   areaRepository;
    private final PuestoRepository puestoRepository;

    // ════════════════════════════════════════════════════════
    // ENDPOINTS PÚBLICOS (usados por formularios de trabajador)
    // Devuelven solo los registros activos
    // ════════════════════════════════════════════════════════

    @GetMapping("/generos")
    public ResponseEntity<List<Genero>> getGeneros() {
        return ResponseEntity.ok(generoRepository.findByActivoTrueOrderByGeneroAsc());
    }

    @GetMapping("/areas")
    public ResponseEntity<List<Area>> getAreas() {
        return ResponseEntity.ok(areaRepository.findByActivoTrueOrderByAreaAsc());
    }

    @GetMapping("/areas/{idArea}/puestos")
    public ResponseEntity<List<Puesto>> getPuestosByArea(@PathVariable Integer idArea) {
        return ResponseEntity.ok(puestoRepository.findByArea_IdAreaAndActivoTrue(idArea));
    }

    // ════════════════════════════════════════════════════════
    // CRUD GÉNEROS — Solo SuperAdmin
    // ════════════════════════════════════════════════════════

    @PreAuthorize("hasRole('SUPERADMIN')")
    @GetMapping("/admin/generos")
    public ResponseEntity<List<GeneroResponse>> listarGeneros() {
        List<GeneroResponse> lista = generoRepository.findAllByOrderByGeneroAsc().stream()
                .map(g -> GeneroResponse.builder()
                        .idGenero(g.getIdGenero())
                        .genero(g.getGenero())
                        .activo(g.isActivo())
                        .build())
                .toList();
        return ResponseEntity.ok(lista);
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping("/admin/generos")
    public ResponseEntity<GeneroResponse> crearGenero(@Valid @RequestBody GeneroRequest req) {
        if (generoRepository.existsByGeneroIgnoreCase(req.getGenero()))
            throw new BusinessException("Ya existe un género con el nombre: " + req.getGenero());

        Genero saved = generoRepository.save(
                Genero.builder().genero(req.getGenero().trim()).activo(true).build());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toGeneroResponse(saved));
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @PutMapping("/admin/generos/{id}")
    public ResponseEntity<GeneroResponse> editarGenero(
            @PathVariable Integer id, @Valid @RequestBody GeneroRequest req) {

        Genero genero = generoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Género no encontrado"));

        if (generoRepository.existsByGeneroIgnoreCaseAndIdGeneroNot(req.getGenero(), id))
            throw new BusinessException("Ya existe un género con el nombre: " + req.getGenero());

        genero.setGenero(req.getGenero().trim());
        return ResponseEntity.ok(toGeneroResponse(generoRepository.save(genero)));
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @PatchMapping("/admin/generos/{id}/toggle")
    public ResponseEntity<GeneroResponse> toggleGenero(@PathVariable Integer id) {
        Genero genero = generoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Género no encontrado"));
        genero.setActivo(!genero.isActivo());
        return ResponseEntity.ok(toGeneroResponse(generoRepository.save(genero)));
    }

    // ════════════════════════════════════════════════════════
    // CRUD ÁREAS — Solo SuperAdmin
    // ════════════════════════════════════════════════════════

    @PreAuthorize("hasRole('SUPERADMIN')")
    @GetMapping("/admin/areas")
    public ResponseEntity<List<AreaResponse>> listarAreas() {
        List<AreaResponse> lista = areaRepository.findAllByOrderByAreaAsc().stream()
                .map(a -> AreaResponse.builder()
                        .idArea(a.getIdArea())
                        .area(a.getArea())
                        .activo(a.isActivo())
                        .build())
                .toList();
        return ResponseEntity.ok(lista);
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping("/admin/areas")
    public ResponseEntity<AreaResponse> crearArea(@Valid @RequestBody AreaRequest req) {
        if (areaRepository.existsByAreaIgnoreCase(req.getArea()))
            throw new BusinessException("Ya existe un área con el nombre: " + req.getArea());

        Area saved = areaRepository.save(
                Area.builder().area(req.getArea().trim()).activo(true).build());

        return ResponseEntity.status(HttpStatus.CREATED).body(toAreaResponse(saved));
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @PutMapping("/admin/areas/{id}")
    public ResponseEntity<AreaResponse> editarArea(
            @PathVariable Integer id, @Valid @RequestBody AreaRequest req) {

        Area area = areaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Área no encontrada"));

        if (areaRepository.existsByAreaIgnoreCaseAndIdAreaNot(req.getArea(), id))
            throw new BusinessException("Ya existe un área con el nombre: " + req.getArea());

        area.setArea(req.getArea().trim());
        return ResponseEntity.ok(toAreaResponse(areaRepository.save(area)));
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @PatchMapping("/admin/areas/{id}/toggle")
    public ResponseEntity<AreaResponse> toggleArea(@PathVariable Integer id) {
        Area area = areaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Área no encontrada"));
        area.setActivo(!area.isActivo());
        return ResponseEntity.ok(toAreaResponse(areaRepository.save(area)));
    }

    // ════════════════════════════════════════════════════════
    // CRUD PUESTOS — Solo SuperAdmin
    // ════════════════════════════════════════════════════════

    @PreAuthorize("hasRole('SUPERADMIN')")
    @GetMapping("/admin/puestos")
    public ResponseEntity<List<PuestoResponse>> listarPuestos() {
        List<PuestoResponse> lista = puestoRepository.findAllByOrderByArea_AreaAscPuestoAsc().stream()
                .map(this::toPuestoResponse)
                .toList();
        return ResponseEntity.ok(lista);
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping("/admin/puestos")
    public ResponseEntity<PuestoResponse> crearPuesto(@Valid @RequestBody PuestoRequest req) {
        Area area = areaRepository.findById(req.getIdArea())
                .orElseThrow(() -> new ResourceNotFoundException("Área no encontrada"));

        if (puestoRepository.existsByPuestoIgnoreCaseAndArea_IdArea(req.getPuesto(), req.getIdArea()))
            throw new BusinessException("Ya existe un puesto '"+ req.getPuesto() +"' en el área seleccionada.");

        Puesto saved = puestoRepository.save(Puesto.builder()
                .puesto(req.getPuesto().trim())
                .descripcionPuesto(req.getDescripcionPuesto())
                .area(area)
                .activo(true)
                .build());

        return ResponseEntity.status(HttpStatus.CREATED).body(toPuestoResponse(saved));
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @PutMapping("/admin/puestos/{id}")
    public ResponseEntity<PuestoResponse> editarPuesto(
            @PathVariable Integer id, @Valid @RequestBody PuestoRequest req) {

        Puesto puesto = puestoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Puesto no encontrado"));

        Area area = areaRepository.findById(req.getIdArea())
                .orElseThrow(() -> new ResourceNotFoundException("Área no encontrada"));

        if (puestoRepository.existsByPuestoIgnoreCaseAndArea_IdAreaAndIdPuestoNot(
                req.getPuesto(), req.getIdArea(), id))
            throw new BusinessException("Ya existe un puesto '"+ req.getPuesto() +"' en el área seleccionada.");

        puesto.setPuesto(req.getPuesto().trim());
        puesto.setDescripcionPuesto(req.getDescripcionPuesto());
        puesto.setArea(area);

        return ResponseEntity.ok(toPuestoResponse(puestoRepository.save(puesto)));
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @PatchMapping("/admin/puestos/{id}/toggle")
    public ResponseEntity<PuestoResponse> togglePuesto(@PathVariable Integer id) {
        Puesto puesto = puestoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Puesto no encontrado"));
        puesto.setActivo(!puesto.isActivo());
        return ResponseEntity.ok(toPuestoResponse(puestoRepository.save(puesto)));
    }

    // ── helpers de mapeo ────────────────────────────────────
    private GeneroResponse toGeneroResponse(Genero g) {
        return GeneroResponse.builder()
                .idGenero(g.getIdGenero()).genero(g.getGenero()).activo(g.isActivo()).build();
    }

    private AreaResponse toAreaResponse(Area a) {
        return AreaResponse.builder()
                .idArea(a.getIdArea()).area(a.getArea()).activo(a.isActivo()).build();
    }

    private PuestoResponse toPuestoResponse(Puesto p) {
        return PuestoResponse.builder()
                .idPuesto(p.getIdPuesto())
                .puesto(p.getPuesto())
                .descripcionPuesto(p.getDescripcionPuesto())
                .idArea(p.getArea().getIdArea())
                .areaNombre(p.getArea().getArea())
                .activo(p.isActivo())
                .build();
    }
}