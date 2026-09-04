package com.idat.asistencia.service;

import com.idat.asistencia.dto.ParametrosDTOs.*;
import com.idat.asistencia.model.entity.ParametrosGeneralesAsistencia;
import com.idat.asistencia.model.entity.ParametrosQuincena;

/**
 * Acceso a la configuracion global (CU26, CU27).
 * Ambos registros son unicos y se crean con valores por defecto la
 * primera vez que se leen, de modo que el sistema arranque configurado.
 */
public interface ParametrosService {

    ParametrosGeneralesAsistencia getGenerales();

    ParametrosQuincena getQuincena();

    ParametrosGeneralesResponse verGenerales();

    ParametrosQuincenaResponse verQuincena();

    ParametrosGeneralesResponse guardarGenerales(ParametrosGeneralesRequest req);

    ParametrosQuincenaResponse guardarQuincena(ParametrosQuincenaRequest req);
}
