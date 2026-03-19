package com.idat.asistencia.service.impl;

import com.idat.asistencia.dto.AsistenciaReporteDTO;
import com.idat.asistencia.model.entity.Asistencia;
import com.idat.asistencia.repository.AsistenciaRepository;
import com.idat.asistencia.service.AsistenciaReporteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AsistenciaReporteServiceImpl implements AsistenciaReporteService {

    private final AsistenciaRepository asistenciaRepo;

    private static final DateTimeFormatter FMT_TIME = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public List<AsistenciaReporteDTO> getReporte(
            String  fechaInicioStr,
            String  fechaFinStr,
            Long    idTrabajador,
            Integer idArea) {

        LocalDate inicio = LocalDate.parse(fechaInicioStr);
        LocalDate fin    = LocalDate.parse(fechaFinStr);

        return asistenciaRepo
                .findReporte(inicio, fin, idTrabajador, idArea)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private AsistenciaReporteDTO toDTO(Asistencia a) {

        // ── Día de la semana ──────────────────────────────────
        String diaSemana = a.getFecha()
                .getDayOfWeek()
                .getDisplayName(TextStyle.FULL, new Locale("es", "PE"));
        diaSemana = Character.toUpperCase(diaSemana.charAt(0)) + diaSemana.substring(1);

        // ── Minutos laborados desde marcas reales ─────────────
        // Usamos min_horas_totales si ya fue calculado; si no,
        // calculamos provisionalmente desde ingreso/salida real.
        Long minutosLaborados = null;
        if (a.getMinHorasTotales() != null) {
            minutosLaborados = a.getMinHorasTotales().longValue();
        } else if (a.getIngresoReal() != null && a.getSalidaReal() != null) {
            minutosLaborados = java.time.Duration
                    .between(a.getIngresoReal(), a.getSalidaReal())
                    .toMinutes();
            // Descontar refrigerio programado si existe
            if (a.getMinRefrigerioProg() != null)
                minutosLaborados -= a.getMinRefrigerioProg();
        }

        // ── Estado diario (A_TIEMPO / TARDE / FALTA / etc.) ───
        // Viene del campo estadoDiario (compatibilidad con sistema anterior).
        // Si el registro es FALTA o PERMISO, usamos el tipo como estado visual.
        String estadoDiario = a.getEstadoDiario();
        if (estadoDiario == null) {
            estadoDiario = switch (a.getTipo().name()) {
                case "FALTA"   -> "FALTA";
                case "PERMISO" -> "JUSTIFICADO";
                default        -> a.getEstado().name();
            };
        }

        String estadoLabel = switch (estadoDiario) {
            case "A_TIEMPO"    -> "A tiempo";
            case "TARDE"       -> "Tardanza";
            case "FALTA"       -> "Falta";
            case "JUSTIFICADO" -> "Justificado";
            default            -> estadoDiario;
        };

        // ── Datos del trabajador ──────────────────────────────
        var t = a.getTrabajador();
        String nombreCompleto = t.getPNombre() + " "
                + (t.getSNombre() != null ? t.getSNombre() + " " : "")
                + t.getAPaterno() + " " + t.getAMaterno();

        String areaNombre   = (t.getPuesto() != null && t.getPuesto().getArea() != null)
                ? t.getPuesto().getArea().getArea() : "—";
        String puestoNombre = t.getPuesto() != null ? t.getPuesto().getPuesto() : "—";

        return AsistenciaReporteDTO.builder()
                .idTrabajador(t.getIdTrabajador())
                .nombreCompleto(nombreCompleto.trim())
                .nroDocumento(t.getNroDocumento())
                .areaNombre(areaNombre)
                .puestoNombre(puestoNombre)
                .fecha(a.getFecha().toString())
                .diaSemana(diaSemana)
                // ingresoReal/salidaReal son los nuevos nombres de horaEntrada/horaSalida
                .horaEntrada(a.getIngresoReal() != null
                        ? a.getIngresoReal().format(FMT_TIME) : null)
                .horaSalida(a.getSalidaReal() != null
                        ? a.getSalidaReal().format(FMT_TIME) : null)
                .estado(estadoDiario)
                .estadoLabel(estadoLabel)
                .observacion(a.getObservacion())
                .minutosLaborados(minutosLaborados)
                .build();
    }
}