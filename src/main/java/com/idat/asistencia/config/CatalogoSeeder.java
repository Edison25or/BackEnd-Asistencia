package com.idat.asistencia.config;

import com.idat.asistencia.model.entity.*;
import com.idat.asistencia.repository.*;
import com.idat.asistencia.service.ParametrosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Inicializacion de los catalogos y parametros que el sistema necesita
 * para arrancar.
 *
 * ============================================================
 * SOBRE LA PRECARGA DE CATALOGOS
 * ============================================================
 * Turnos se precargan siempre: sin al menos uno no puede crearse ningun
 * esquema de horario (RN-18), y sin esquema no hay programacion ni
 * marcacion. El sistema quedaria inoperable de fabrica.
 *
 * Tipos de Ausencia y Motivos de Cese se precargan solo si la propiedad
 * asistencia.seed.catalogos esta activa. AL-03 establece que estos
 * catalogos se entregan vacios para que la empresa defina los suyos, de
 * modo que conviene DESACTIVARLA antes de la entrega:
 *
 *     asistencia.seed.catalogos=false
 *
 * Los valores cargados son una base razonable para una planta peruana,
 * pero no reemplazan la definicion del cliente. Deben revisarse con
 * Recursos Humanos antes de operar (DEP-04).
 *
 * Feriados NO se precargan en ningun caso: cambian cada ano por decreto y
 * un calendario desactualizado produciria calculos de pago incorrectos en
 * silencio, que es peor que no tener ninguno (RN-41).
 *
 * Los parametros generales y de quincena se crean con sus valores por
 * defecto, de modo que el sistema pueda operar desde el primer arranque
 * sin configuracion manual previa.
 *
 * Tambien se completa el codigo de barras de los trabajadores que no lo
 * tengan, que es el caso de cualquier registro creado antes de esta
 * version (CU11).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogoSeeder {

    private final TurnoRepository        turnoRepo;
    private final TipoAusenciaRepository tipoAusenciaRepo;
    private final MotivoCeseRepository   motivoCeseRepo;
    private final TrabajadorRepository   trabajadorRepo;
    private final ParametrosService      parametrosService;

    /** Poner en false antes de la entrega al cliente (AL-03). */
    @Value("${asistencia.seed.catalogos:true}")
    private boolean sembrarCatalogos;

    /**
     * Se dispara con ApplicationReadyEvent, que Spring publica DESPUES de
     * ejecutar todos los CommandLineRunner.
     *
     * No es un CommandLineRunner con @Order porque DataSeeder no declara
     * ninguno: Spring le asigna la menor precedencia posible y no hay
     * valor de @Order que garantice correr despues de el. Si este seeder
     * corriera primero, los trabajadores que DataSeeder acaba de crear
     * quedarian sin codigo de barras en una base nueva y no podrian
     * marcar, porque findByCodigoBarras no los encontraria.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void inicializar() {
        sembrarTurnos();
        sembrarTiposAusencia();
        sembrarMotivosCese();
        avisarFeriadosVacios();
        inicializarParametros();
        completarCodigosDeBarras();
    }

    private void sembrarTurnos() {
        if (turnoRepo.count() > 0) return;

        turnoRepo.saveAll(List.of(
                Turno.builder().nombre("Dia")
                        .horaInicio(LocalTime.of(6, 0)).horaFin(LocalTime.of(18, 0))
                        .activo(true).build(),
                Turno.builder().nombre("Noche")
                        .horaInicio(LocalTime.of(22, 0)).horaFin(LocalTime.of(6, 0))
                        .activo(true).build()
        ));
        log.info("Catalogo de turnos inicializado: Dia y Noche");
    }

    // ---------- Tipos de ausencia (RN-16) ----------

    /**
     * Base de tipos de ausencia para una planta peruana.
     *
     * El catalogo es COMUN a los dos flujos: permisos planificados (CU16)
     * y faltas justificadas no planificadas (CU17). Lo que distingue a uno
     * de otro es la entidad donde se registra, no el tipo, de modo que
     * cualquier valor puede usarse en cualquiera de los dos.
     */
    private void sembrarTiposAusencia() {
        if (!sembrarCatalogos) {
            if (tipoAusenciaRepo.count() == 0)
                log.warn("El catalogo de Tipos de Ausencia esta vacio y la precarga esta "
                        + "desactivada. No podran registrarse permisos ni faltas "
                        + "justificadas hasta que el Superadministrador lo complete (DEP-04).");
            return;
        }
        if (tipoAusenciaRepo.count() > 0) return;

        tipoAusenciaRepo.saveAll(List.of(
                tipoAusencia("Vacaciones",
                        "Descanso vacacional programado."),
                tipoAusencia("Descanso medico",
                        "Respaldado por certificado medico o descanso de EsSalud."),
                tipoAusencia("Licencia por maternidad",
                        "Descanso pre y postnatal."),
                tipoAusencia("Licencia por paternidad",
                        "Licencia por nacimiento de hijo."),
                tipoAusencia("Licencia por fallecimiento de familiar",
                        "Duelo por familiar directo."),
                tipoAusencia("Licencia por enfermedad grave de familiar",
                        "Atencion a familiar directo en estado grave o terminal."),
                tipoAusencia("Citacion judicial o policial",
                        "Comparecencia ante autoridad. Requiere la notificacion."),
                tipoAusencia("Permiso personal",
                        "Asunto particular acordado con la jefatura."),
                tipoAusencia("Capacitacion",
                        "Formacion o certificacion autorizada por la empresa."),
                tipoAusencia("Licencia sin goce de haber",
                        "Ausencia prolongada autorizada, sin remuneracion.")
        ));

        log.info("Catalogo de Tipos de Ausencia precargado con {} valores. "
                        + "Revisar con Recursos Humanos antes de operar (DEP-04).",
                tipoAusenciaRepo.count());
    }

    private TipoAusencia tipoAusencia(String nombre, String descripcion) {
        return TipoAusencia.builder()
                .nombre(nombre).descripcion(descripcion).activo(true).build();
    }

    // ---------- Motivos de cese (RN-11) ----------

    /**
     * Base de motivos de cese.
     *
     * "Otro" es imprescindible y no debe eliminarse: cuando el motivo no
     * esta tipificado, el detalle se guarda en
     * PeriodoLaboral.detalleMotivoCese. Sin esa opcion, un cese atipico
     * obligaria a forzar un motivo que no corresponde y el historial
     * laboral quedaria falseado.
     */
    private void sembrarMotivosCese() {
        if (!sembrarCatalogos) {
            if (motivoCeseRepo.count() == 0)
                log.warn("El catalogo de Motivos de Cese esta vacio y la precarga esta "
                        + "desactivada. Las bajas se registraran con motivo de texto "
                        + "libre (DEP-04).");
            return;
        }
        if (motivoCeseRepo.count() > 0) return;

        motivoCeseRepo.saveAll(List.of(
                motivoCese("Renuncia voluntaria"),
                motivoCese("Fin de contrato"),
                motivoCese("Periodo de prueba no superado"),
                motivoCese("Mutuo acuerdo"),
                motivoCese("Despido por falta grave"),
                motivoCese("Abandono de trabajo"),
                motivoCese("Jubilacion"),
                motivoCese("Fallecimiento"),
                motivoCese("Otro")
        ));

        log.info("Catalogo de Motivos de Cese precargado con {} valores. "
                        + "Revisar con Recursos Humanos antes de operar (DEP-04).",
                motivoCeseRepo.count());
    }

    private MotivoCese motivoCese(String nombre) {
        return MotivoCese.builder().nombre(nombre).activo(true).build();
    }

    // ---------- Feriados (RN-41) ----------

    /**
     * Los feriados NO se precargan, ni siquiera con la precarga activa.
     *
     * El calendario peruano cambia cada ano: los feriados moviles se
     * desplazan y el Gobierno declara dias no laborables por decreto, a
     * veces con pocos dias de anticipacion. Un calendario precargado y
     * desactualizado computaria mal las horas de feriado sin que nadie lo
     * note, y ese error llega directo al pago. Es peor que no tener
     * ninguno.
     *
     * El registro es manual y con vista previa del impacto (CU24).
     */
    private void avisarFeriadosVacios() {
        log.info("Los feriados no se precargan: deben registrarse manualmente "
                + "conforme se publiquen (RN-41, CU24).");
    }

    private void inicializarParametros() {
        var generales = parametrosService.getGenerales();
        var quincena  = parametrosService.getQuincena();

        log.info("Parametros de asistencia: P1={} min, P2={} min, P3={} min, "
                        + "confirmacion={}s, anti-rebote={}s",
                generales.getMaxAnticipacionEntrada(), generales.getMaxExcesoSalida(),
                generales.getTopeCombinado(), generales.getVentanaConfirmacionSeg(),
                generales.getIntervaloAntirreboteSeg());

        log.info("Corte de quincena: dia {} a las {}",
                quincena.getDiaCorteIntermedio(), quincena.getHoraCorte());
    }

    /**
     * Completa el codigo de barras de los trabajadores que no lo tengan.
     *
     * Un trabajador sin codigo no puede marcar: findByCodigoBarras no lo
     * encuentra y el lector responde "Trabajador no encontrado".
     */
    private void completarCodigosDeBarras() {
        List<Trabajador> sinCodigo = trabajadorRepo.findAll().stream()
                .filter(t -> t.getCodigoBarras() == null || t.getCodigoBarras().isBlank())
                .toList();

        if (sinCodigo.isEmpty()) return;

        for (Trabajador t : sinCodigo) {
            t.setCodigoBarras(String.valueOf(t.getIdTrabajador()));
            t.setFechaGeneracionCarnet(LocalDateTime.now());
        }
        trabajadorRepo.saveAll(sinCodigo);

        log.info("Codigo de barras asignado a {} trabajador(es) que no lo tenian",
                sinCodigo.size());
    }
}
