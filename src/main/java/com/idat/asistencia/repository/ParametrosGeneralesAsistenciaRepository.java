package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.ParametrosGeneralesAsistencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Registro unico. El servicio debe leerlo siempre con
 * findById(ParametrosGeneralesAsistencia.ID_UNICO) y crearlo con valores
 * por defecto si no existe, de modo que el sistema arranque configurado.
 */
@Repository
public interface ParametrosGeneralesAsistenciaRepository
        extends JpaRepository<ParametrosGeneralesAsistencia, Integer> {
}
