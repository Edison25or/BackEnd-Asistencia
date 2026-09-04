package com.idat.asistencia.service.impl;

import com.idat.asistencia.dto.AsistenciaReporteDTO;
import com.idat.asistencia.model.entity.Asistencia;
import com.idat.asistencia.model.enums.ResultadoValidacion;
import com.idat.asistencia.repository.AsistenciaRepository;
import com.idat.asistencia.security.SecurityHelper;
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

/**
 * Reporte detallado dia por dia (CU21, HU-38).
 *
 * Es el reporte que usa el Jefe para responderle a un trabajador que
 * reclama, de modo que aqui SI se distingue la hora extra estructural de
 * la excepcional, aunque el consolidado las reporte en un total unico.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AsistenciaReporteServiceImpl implements AsistenciaReporteService {

    private final AsistenciaRepository asistenciaRepo;
    private final SecurityHelper       securityHelper;

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    // forLanguageTag y no Locale.of("es","PE"), que existe recien desde
    // Java 19; el proyecto compila con Java 17. Tampoco new Locale(...),
    // que quedo obsoleto en esa misma version.
    private static final Locale ES_PE = Locale.forLanguageTag("es-PE");

    @Override
    public List<AsistenciaReporteDTO> getReporte(String fechaInicioStr, String fechaFinStr,
                                                 Long idTrabajador, Integer idArea) {
        // El trabajador solo ve sus propios registros
        if (securityHelper.esTrabajador()) {
            idTrabajador = securityHelper.getIdTrabajadorAutenticado();
            idArea = null;
        }

        LocalDate inicio = LocalDate.parse(fechaInicioStr);
        LocalDate fin    = LocalDate.parse(fechaFinStr);

        return asistenciaRepo.findReporte(inicio, fin, idTrabajador, idArea)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    private AsistenciaReporteDTO toDTO(Asistencia a) {
        String dia = a.getFecha().getDayOfWeek().getDisplayName(TextStyle.FULL, ES_PE);
        dia = Character.toUpperCase(dia.charAt(0)) + dia.substring(1);

        var t = a.getTrabajador();
        String nombre = t.getPNombre() + " "
                + (t.getSNombre() != null ? t.getSNombre() + " " : "")
                + t.getAPaterno() + " " + t.getAMaterno();

        int extraExcepcional = 0;
        if (a.getResultadoValidacion() == ResultadoValidacion.APROBADO) {
            extraExcepcional = nz(a.getValMinPrevIng()) + nz(a.getValMinPostSal());
        }

        return AsistenciaReporteDTO.builder()
                .idTrabajador(t.getIdTrabajador())
                .nombreCompleto(nombre.trim())
                .nroDocumento(t.getNroDocumento())
                .areaNombre(t.getArea() != null ? t.getArea().getArea() : "-")
                .puestoNombre(t.getPuesto() != null ? t.getPuesto().getPuesto() : "-")
                .fecha(a.getFecha().toString())
                .diaSemana(dia)
                .turnoNombre(a.getTurno() != null ? a.getTurno().getNombre() : "-")
                .esDiaNoLaborable(a.isEsDiaNoLaborable())
                .horaEntradaProg(a.getIngresoProg() != null ? a.getIngresoProg().format(HHMM) : null)
                .horaSalidaProg(a.getSalidaProg()   != null ? a.getSalidaProg().format(HHMM)  : null)
                .horaEntrada(a.getIngresoReal() != null ? a.getIngresoReal().format(HHMM) : null)
                .horaSalida(a.getSalidaReal()   != null ? a.getSalidaReal().format(HHMM)  : null)
                .tipo(a.getTipo().name())
                .tipoLabel(etiqueta(a))
                .estado(a.getEstado().name())
                .requiereRevision(a.isRequiereRevision())
                .minTardanza(nz(a.getMinTardanza()))
                .minSalTemprana(nz(a.getMinSalTemprana()))
                .minutosLaborados(nz(a.getMinHorasTotales()))
                .minutosFeriado(nz(a.getMinutosFeriado()))
                .minExtraEstructural(nz(a.getMinExtraProg()))
                .minExtraExcepcional(extraExcepcional)
                .observacion(a.getObservacion())
                .build();
    }

    private String etiqueta(Asistencia a) {
        if (a.getPermiso() != null)          return "Permiso";
        if (a.getFaltaJustificada() != null) return "Falta justificada";
        return switch (a.getTipo()) {
            case PROGRAMADA               -> nz(a.getMinTardanza()) > 0 ? "Tardanza" : "A tiempo";
            case HORA_EXTRA_NO_PROGRAMADA -> "Hora extra no programada";
            case NO_PROGRAMADA            -> "No programada";
            case MARCACION_INCOMPLETA     -> "Marcacion incompleta";
            case FALTA_INJUSTIFICADA      -> "Falta injustificada";
            case CONTINGENCIA             -> "Contingencia";
        };
    }

    private int nz(Integer v) { return v != null ? v : 0; }
}
