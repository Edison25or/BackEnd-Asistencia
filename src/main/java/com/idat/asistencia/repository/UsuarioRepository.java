package com.idat.asistencia.repository;

import com.idat.asistencia.model.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
    Optional<Usuario> findByUsername(String username);

    Optional<Usuario> findByTrabajador_IdTrabajador(Integer IdTrabajador);

}
