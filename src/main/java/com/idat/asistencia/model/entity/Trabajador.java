package com.idat.asistencia.model.entity;

import com.idat.asistencia.model.enums.EstadoTrabajador;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.Objects;
import com.idat.asistencia.model.enums.Parentesco;

@Entity
@Table(name = "trabajadores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trabajador {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "trabajador_seq")
    @SequenceGenerator(name = "trabajador_seq", sequenceName = "trabajador_id_seq", initialValue = 10001, allocationSize = 1)
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

    @Column(length = 100, unique = true)
    private String email;

    @Column(name = "contacto_emergencias", length = 100)
    private String contactoEmergencias;

    @Enumerated(EnumType.STRING)
    @Column(name = "parentesco", length = 20)
    private Parentesco parentesco;

    @Column(name = "nro_contacto", length = 20)
    private String nroContacto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_genero", nullable = false)
    private Genero genero;

    // ... tus otros campos ...

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20, nullable = false)
    @Builder.Default // Si usas @Builder, esto asegura que el valor por defecto se aplique
    private EstadoTrabajador estado = EstadoTrabajador.ACTIVO;

    // ... tus relaciones (genero, puesto, usuario) ...

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_puesto", nullable = false)
    private Puesto puesto;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Trabajador)) return false;
        Trabajador that = (Trabajador) o;
        return nroDocumento != null && Objects.equals(nroDocumento, that.nroDocumento);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }


    // Relación bidireccional con Usuario
    // CascadeType.ALL permite que al guardar un trabajador, se pueda guardar su usuario asociado automáticamente
    @OneToOne(mappedBy = "trabajador", cascade = CascadeType.ALL)
    private Usuario usuario;

}
