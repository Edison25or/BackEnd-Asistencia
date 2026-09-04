package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.ParametrosQuincena;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Registro unico. Ver nota en ParametrosGeneralesAsistenciaRepository. */
@Repository
public interface ParametrosQuincenaRepository
        extends JpaRepository<ParametrosQuincena, Integer> {
}
