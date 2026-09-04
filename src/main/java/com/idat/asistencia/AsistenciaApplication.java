package com.idat.asistencia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * EnableScheduling activa el proceso de cierre diario de jornadas
 * (CU29, RN-42), que es el unico productor de los estados de falta
 * injustificada y marcacion incompleta. Sin esta anotacion el proceso
 * queda inerte y las ausencias no dejan rastro.
 */
@SpringBootApplication
@EnableScheduling
public class AsistenciaApplication {

    public static void main(String[] args) {
        SpringApplication.run(AsistenciaApplication.class, args);
    }
}
