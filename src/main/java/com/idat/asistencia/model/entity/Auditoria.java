package com.idat.asistencia.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "auditoria",
        indexes = {
                @Index(name = "idx_aud_tabla_registro", columnList = "tabla, id_registro"),
                @Index(name = "idx_aud_usuario",        columnList = "id_usuario"),
                @Index(name = "idx_aud_fecha",           columnList = "fecha")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre de la tabla afectada: 'trabajadores', 'grupos_trabajo', etc. */
    @Column(nullable = false, length = 50)
    private String tabla;

    /** ID del registro modificado en dicha tabla */
    @Column(name = "id_registro", nullable = false)
    private Long idRegistro;

    /** CREAR | MODIFICAR | DESHABILITAR | HABILITAR | RESET_PASSWORD | CAMBIAR_ROL | CESAR | REINGRESAR */
    @Column(nullable = false, length = 30)
    private String accion;

    /** Campo específico que cambió (null para acciones tipo CREAR/CESAR) */
    @Column(length = 60)
    private String campo;

    @Column(name = "valor_anterior", columnDefinition = "TEXT")
    private String valorAnterior;

    @Column(name = "valor_nuevo", columnDefinition = "TEXT")
    private String valorNuevo;

    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "nombre_usuario", length = 100)
    private String nombreUsuario;

    @Column(name = "ip_origen", length = 45)
    private String ipOrigen;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime fecha = LocalDateTime.now();
}