package com.idat.asistencia.model.entity;

import com.idat.asistencia.model.enums.EstadoQuincena;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Periodo de pago. Se autogenera al confirmarse una programacion semanal
 * cuyos dias no pertenecen a ninguna quincena existente (RN-35, CU14).
 *
 * ============================================================
 * CAMBIOS RESPECTO DEL PROTOTIPO
 * ============================================================
 * 1. inicio y fin pasan de LocalDate + horaCorte a LocalDateTime. Los
 *    helpers getInicioReal() y getFinReal() desaparecen porque ahora los
 *    limites SON los campos.
 *
 * 2. El rango es SEMIABIERTO [inicio, fin). Con limites cerrados, dos
 *    quincenas consecutivas se pisan exactamente en el instante de corte
 *    y una jornada podria pertenecer a las dos.
 *
 * 3. Se elimina la creacion manual. El metodo crearQuincena() y su
 *    endpoint desaparecen: la quincena se resuelve por dia de jornada
 *    dentro de confirmarSemana().
 *
 * 4. cerradoPor y reabiertoPor pasan de Long suelto a relacion real con
 *    Usuario.
 */
@Entity
@Table(name = "quincenas",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_quincena", columnNames = {"anio", "mes", "numero"}),
       indexes = @Index(name = "ix_quincena_rango", columnList = "inicio, fin"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Quincena {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_quincena")
    private Long idQuincena;

    @Column(nullable = false)
    private Integer anio;

    /** 1 a 12 */
    @Column(nullable = false)
    private Integer mes;

    /** 1 = primera quincena, 2 = segunda */
    @Column(nullable = false)
    private Integer numero;

    /** Limite inferior, inclusivo. Incluye la hora de corte. */
    @Column(nullable = false)
    private LocalDateTime inicio;

    /** Limite superior, EXCLUSIVO. Incluye la hora de corte. */
    @Column(nullable = false)
    private LocalDateTime fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private EstadoQuincena estado = EstadoQuincena.ABIERTA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cerrado_por")
    private Usuario cerradoPor;

    @Column(name = "cerrado_en")
    private LocalDateTime cerradoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reabierto_por")
    private Usuario reabiertoPor;

    @Column(name = "reabierto_en")
    private LocalDateTime reabiertoEn;

    /** Obligatorio al reabrir, entre 10 y 500 caracteres (RN-38). */
    @Column(name = "motivo_reapertura", length = 500)
    private String motivoReapertura;

    // ---------- Helpers ----------

    /**
     * true si el instante pertenece a esta quincena.
     * Rango semiabierto: una jornada que entra exactamente a la hora de
     * corte pertenece a la quincena SIGUIENTE.
     */
    @Transient
    public boolean contiene(LocalDateTime instante) {
        return instante != null
            && !instante.isBefore(inicio)
            && instante.isBefore(fin);
    }

    @Transient
    public boolean isAbierta() {
        return estado == EstadoQuincena.ABIERTA;
    }

    /** Ej: "1ra quincena de marzo 2026" */
    @Transient
    public String getDescripcion() {
        String[] meses = {"", "enero", "febrero", "marzo", "abril", "mayo", "junio",
                "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
        return (numero == 1 ? "1ra" : "2da") + " quincena de " + meses[mes] + " " + anio;
    }
}
