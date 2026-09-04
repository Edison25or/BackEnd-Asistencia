package com.idat.asistencia.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Registro de acciones sensibles del sistema (RN-02, CU28).
 * Retencion minima: tres anios (RN-39).
 *
 * ============================================================
 * CAMBIOS RESPECTO DEL PROTOTIPO
 * ============================================================
 * 1. Se agrega la relacion real con Usuario. El prototipo declaraba un
 *    idUsuario suelto que buildBase() NUNCA asignaba, de modo que todo el
 *    historial de auditoria quedaba sin autor. Se conserva ademas
 *    nombreUsuario como copia del nombre al momento del evento, que
 *    sobrevive aunque el usuario cambie de nombre despues.
 *
 * 2. Se agrega el campo motivo, para las acciones que exigen
 *    justificacion (cese, reapertura, correccion de marcacion). El
 *    prototipo lo concatenaba dentro de valorNuevo, lo que impedia
 *    filtrar por motivo y mezclaba dos cosas distintas.
 */
@Entity
@Table(name = "auditoria",
       indexes = {
           @Index(name = "ix_audit_fecha",   columnList = "fecha"),
           @Index(name = "ix_audit_tabla",   columnList = "tabla, id_registro"),
           @Index(name = "ix_audit_usuario", columnList = "id_usuario")
       })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre de la entidad afectada. */
    @Column(nullable = false, length = 60)
    private String tabla;

    @Column(name = "id_registro")
    private Long idRegistro;

    /**
     * CREAR, MODIFICAR, DESHABILITAR, HABILITAR, RESET_PASSWORD,
     * CAMBIAR_ROL, CESAR, REINGRESAR, REABRIR, CIERRE_DIARIO, entre otras.
     */
    @Column(nullable = false, length = 40)
    private String accion;

    @Column(length = 60)
    private String campo;

    @Column(name = "valor_anterior", columnDefinition = "TEXT")
    private String valorAnterior;

    @Column(name = "valor_nuevo", columnDefinition = "TEXT")
    private String valorNuevo;

    /** Justificacion, para las acciones que la exigen (RN-02). */
    @Column(length = 500)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    /** Copia del nombre al momento del evento. */
    @Column(name = "nombre_usuario", length = 100)
    private String nombreUsuario;

    @Column(name = "ip_origen", length = 45)
    private String ipOrigen;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime fecha = LocalDateTime.now();
}
