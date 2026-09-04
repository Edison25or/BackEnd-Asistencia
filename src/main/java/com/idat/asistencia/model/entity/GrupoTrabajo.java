package com.idat.asistencia.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Agrupacion de trabajadores de una misma area, para asignarles horario
 * de forma masiva (CU13).
 *
 * ============================================================
 * CAMBIOS RESPECTO DEL PROTOTIPO
 * ============================================================
 * 1. La relacion con Trabajador pasa de @ManyToMany a @OneToMany
 *    (RN-21). Con muchos a muchos, que un trabajador pertenezca a un solo
 *    grupo solo podia garantizarse por codigo; ahora es estructuralmente
 *    imposible que pertenezca a dos, porque la pertenencia vive en una
 *    unica columna de Trabajador.
 *
 * 2. Se agrega la FK obligatoria a Area (RN-20). El prototipo validaba
 *    que un trabajador no estuviera en dos grupos, pero no que todos los
 *    miembros fueran de la misma area.
 */
@Entity
@Table(name = "grupos_trabajo")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GrupoTrabajo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_grupo")
    private Integer idGrupo;

    @Column(nullable = false, length = 50, unique = true)
    private String nombre;

    @Column(length = 150)
    private String descripcion;

    /** Todos los miembros deben pertenecer a esta area (RN-20). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_area", nullable = false)
    private Area area;

    /**
     * Lado inverso. La columna real (id_grupo) vive en trabajadores.
     * Solo lectura: para mover un trabajador se asigna su campo
     * grupoTrabajo, no se manipula esta coleccion.
     */
    @OneToMany(mappedBy = "grupoTrabajo", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Trabajador> trabajadores = new ArrayList<>();
}
