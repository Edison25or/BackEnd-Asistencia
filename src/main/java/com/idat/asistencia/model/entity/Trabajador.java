package com.idat.asistencia.model.entity;

import com.idat.asistencia.model.enums.EstadoTrabajador;
import com.idat.asistencia.model.enums.Parentesco;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * ============================================================
 * CAMBIOS RESPECTO DEL PROTOTIPO
 * ============================================================
 * 1. Se agregan codigoBarras y fechaGeneracionCarnet (CU11, RT-02).
 *    El prototipo no tenia ningun campo de codigo: marcar() parseaba el
 *    identificador del propio codigo escaneado ("10023IN" -> id 10023 mas
 *    sufijo IN u OU), de modo que existian DOS codigos por trabajador,
 *    lo que RT-02 prohibe.
 *
 *    Guardar el codigo como campo propio, aunque su valor inicial sea el
 *    identificador, desacopla el algoritmo de marcacion de la forma del
 *    codigo. Cambiarlo por un valor aleatorio no adivinable el dia de
 *    manana es reemplazar el generador, sin tocar marcar().
 *
 * 2. Se agrega grupoTrabajo. La pertenencia a un grupo pasa a vivir aqui,
 *    en una unica columna, lo que hace estructuralmente imposible que un
 *    trabajador este en dos grupos (RN-21).
 */
@Entity
@Table(name = "trabajadores",
       indexes = @Index(name = "ix_trab_codigo_barras", columnList = "codigo_barras"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Trabajador {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "trabajador_seq")
    @SequenceGenerator(name = "trabajador_seq", sequenceName = "trabajador_id_seq",
                       initialValue = 10001, allocationSize = 1)
    @Column(name = "id_trabajador")
    private Long idTrabajador;

    @Column(name = "doc_identidad", length = 20, nullable = false)
    private String docIdentidad;

    @Column(name = "nro_documento", length = 20, nullable = false, unique = true)
    private String nroDocumento;

    @Column(name = "p_nombre", length = 50, nullable = false)
    private String pNombre;

    @Column(name = "s_nombre", length = 50)
    private String sNombre;

    @Column(name = "a_paterno", length = 50, nullable = false)
    private String aPaterno;

    @Column(name = "a_materno", length = 50, nullable = false)
    private String aMaterno;

    @Column(name = "fecha_nac", nullable = false)
    private LocalDate fechaNac;

    @Column(length = 255)
    private String direccion;

    @Column(length = 20)
    private String telefono;

    /** Es el nombre de usuario de acceso. Ver decision pendiente PD-04. */
    @Column(length = 100, unique = true)
    private String email;

    @Column(name = "contacto_emergencias", length = 100)
    private String contactoEmergencias;

    @Enumerated(EnumType.STRING)
    @Column(name = "parentesco", length = 20)
    private Parentesco parentesco;

    @Column(name = "nro_contacto", length = 20)
    private String nroContacto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_genero", nullable = false)
    private Genero genero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_puesto", nullable = false)
    private Puesto puesto;

    /**
     * Grupo al que pertenece. Un trabajador esta en un unico grupo a la
     * vez (RN-21) y el grupo debe ser de su misma area (RN-20).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_grupo")
    private GrupoTrabajo grupoTrabajo;

    // ---------- Carne (CU11) ----------

    /**
     * Codigo unico por trabajador. Un solo codigo, sin sufijos de entrada
     * ni salida: el sistema deduce cual corresponde segun el estado de la
     * jornada abierta (RT-02).
     *
     * Al reponer el carne se sobrescribe, lo que invalida el anterior.
     */
    @Column(name = "codigo_barras", length = 40, unique = true)
    private String codigoBarras;

    @Column(name = "fecha_generacion_carnet")
    private LocalDateTime fechaGeneracionCarnet;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20, nullable = false)
    @Builder.Default
    private EstadoTrabajador estado = EstadoTrabajador.ACTIVO;

    @OneToOne(mappedBy = "trabajador", cascade = CascadeType.ALL)
    private Usuario usuario;

    // ---------- Helpers ----------

    @Transient
    public String getNombreCompleto() {
        return pNombre + " " + aPaterno + " " + aMaterno;
    }

    @Transient
    public boolean isActivo() {
        return estado == EstadoTrabajador.ACTIVO;
    }

    /** Area del trabajador via puesto. Null si falta alguno de los dos. */
    @Transient
    public Area getArea() {
        return (puesto != null) ? puesto.getArea() : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Trabajador that)) return false;
        return nroDocumento != null && Objects.equals(nroDocumento, that.nroDocumento);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
