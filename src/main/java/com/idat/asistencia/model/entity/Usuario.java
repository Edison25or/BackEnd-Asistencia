package com.idat.asistencia.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Credenciales de acceso. Todo usuario del sistema es, ante todo, un
 * trabajador (RN-03).
 *
 * ============================================================
 * CAMBIO RESPECTO DEL PROTOTIPO
 * ============================================================
 * Se agrega debeCambiarPassword como campo PERSISTIDO (RN-07).
 *
 * El prototipo inferia esa condicion comparando el hash de la contrasena
 * actual contra el numero de documento del trabajador. Esa comparacion se
 * rompe en cuanto el Superadministrador corrige el documento (CU08): el
 * usuario que ya habia cambiado su clave vuelve a quedar marcado, o al
 * reves, uno que nunca la cambio deja de estarlo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "usuarios")
public class Usuario implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer idUsuario;

    /** Igual al correo del trabajador. */
    @Column(unique = true, nullable = false)
    private String username;

    /** Hash fuerte con sal. Nunca texto plano. */
    @Column(nullable = false)
    private String password;

    /** ROLE_TRABAJADOR, ROLE_SUPERVISOR, ROLE_JEFE, ROLE_ADMIN, ROLE_SUPERADMIN */
    @Column(nullable = false)
    private String rol;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_trabajador", referencedColumnName = "id_trabajador")
    private Trabajador trabajador;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /**
     * true mientras el usuario conserve la contrasena temporal. Bloquea
     * cualquier otra accion hasta que la cambie (RN-07, CU01).
     * Se activa al crear el usuario y al restablecer la contrasena (CU02).
     */
    @Column(name = "debe_cambiar_password", nullable = false)
    @Builder.Default
    private boolean debeCambiarPassword = true;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(rol));
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return this.enabled; }
}
