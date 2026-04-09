package com.idat.asistencia.config;

import com.idat.asistencia.model.entity.*;
import com.idat.asistencia.model.enums.EstadoTrabajador;
import com.idat.asistencia.model.enums.Parentesco;
import com.idat.asistencia.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final TrabajadorRepository trabajadorRepository;
    private final GeneroRepository generoRepository;
    private final PuestoRepository puestoRepository;
    private final AreaRepository areaRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        if (usuarioRepository.count() == 0) {
            System.out.println("=================================================");
            System.out.println("Iniciando carga masiva optimizada (Matrices)...");

            seedGeneros();
            seedAreas();
            seedPuestos();
            cargarTrabajadoresYUsuarios();
            crearSuperAdmin();

            System.out.println("Carga masiva finalizada exitosamente.");
            System.out.println("=================================================");
        }
    }

    private void seedGeneros() {
        if (generoRepository.count() > 0) return;

        List<Genero> lista = new ArrayList<>();
        for (String nombre : new String[]{"Masculino", "Femenino"}) {
            Genero g = new Genero();
            g.setGenero(nombre);
            g.setActivo(true); // <-- Se agregó el estado activo
            lista.add(g);
        }
        generoRepository.saveAll(lista);
    }

    private void seedAreas() {
        if (areaRepository.count() > 0) return;

        List<Area> lista = new ArrayList<>();
        for (String nombre : new String[]{
                "Administración", "Producción", "Calidad", "Mantenimiento",
                "Contabilidad", "Comercial", "Exportaciones", "Vigilancia", "Ssoma"}) {
            Area a = new Area();
            a.setArea(nombre);
            a.setActivo(true); // <-- Se agregó el estado activo
            lista.add(a);
        }
        areaRepository.saveAll(lista);
    }

    private void seedPuestos() {
        if (puestoRepository.count() > 0) return;

        // Orden: nombre, descripcion, id_area
        Object[][] data = {
                {"Gerente Administrativo", "Descripción de Puesto: Gerente Administrativo", 1},
                {"Asistente Administrativo", "Descripción de Puesto: Asistente Administrativo", 1},
                {"Jefe de Producción", "Descripción de Puesto: Jefe de Producción", 2},
                {"Supervisor de Producción", "Descripción de Puesto: Supervisor de Producción", 2},
                {"Supervisor de Almacén", "Descripción de Puesto: Supervisor de Almacén", 2},
                {"Auxiliar de Almacén", "Descripción de Puesto: Auxiliar de Almacén", 2},
                {"Líder de Línea", "Descripción de Puesto: Líder de Línea", 2},
                {"Ayudante de Línea", "Descripción de Puesto: Ayudante de Línea", 2},
                {"Jefe de Calidad", "Descripción de Puesto: Jefe de Calidad", 3},
                {"Supervisor de Calidad", "Descripción de Puesto: Supervisor de Calidad", 3},
                {"Asistente de Calidad", "Descripción de Puesto: Asistente de Calidad", 3},
                {"Auxiliar de Limpieza", "Descripción de Puesto: Auxiliar de Limpieza", 3},
                {"Técnico Mécanico", "Descripción de Puesto: Técnico Mécanico", 4},
                {"Técnico Electricista", "Descripción de Puesto: Técnico Electricista", 4},
                {"Jefe de Contabilidad", "Descripción de Puesto: Jefe de Contabilidad", 5},
                {"Asitente Contable", "Descripción de Puesto: Asitente Contable", 5},
                {"Gerente Comercial", "Descripción de Puesto: Gerente Comercial", 6},
                {"Asistente Comercial", "Descripción de Puesto: Asistente Comercial", 6},
                {"Responsable de Tienda", "Descripción de Puesto: Responsable de Tienda", 6},
                {"Jefe de Exportaciones", "Descripción de Puesto: Jefe de Exportaciones", 7},
                {"Asistente de Exportaciones", "Descripción de Puesto: Asistente de Exportaciones", 7},
                {"Seguridad de Planta", "Descripción de Puesto: Seguridad de Planta", 8},
                {"Jefe de Ssoma", "Descripción de Puesto: Jefe de Ssoma", 9},
                {"Asistente Ssoma", "Descripción de Puesto: Asistente Ssoma", 9}
        };

        List<Puesto> lista = new ArrayList<>();
        for (Object[] row : data) {
            Puesto p = new Puesto();
            p.setPuesto((String) row[0]);
            p.setDescripcionPuesto((String) row[1]);
            int idArea = ((Number) row[2]).intValue();
            p.setArea(areaRepository.findById(idArea).orElseThrow(
                    () -> new RuntimeException("Área no encontrada: " + idArea)
            ));
            p.setActivo(true); // <-- Se agregó el estado activo
            lista.add(p);
        }
        puestoRepository.saveAll(lista);
    }

    private void cargarTrabajadoresYUsuarios() {
        // MATRIZ DE DATOS
        // Orden: p_nombre, s_nombre, a_paterno, a_materno, contacto, direccion, doc_id, nro_doc, fecha_nac, nro_cont, telf, id_genero, id_puesto, parentesco, email, estado
        Object[][] trabajadoresData = {
                {"David", "", "Ruiz", "Pizarro", "Contacto de David Ruiz", "Dirección David Ruiz", "DNI", "10006103", "1976-06-18", "998164295", "998164295", 1L, 1L, "OTRO", "David10001@email.com", "ACTIVO"},
                {"Freddy", "Alex", "Salazar", "Arohuillca", "Contacto de Freddy Salazar", "Dirección Freddy Salazar", "DNI", "71307647", "2000-01-01", "987654321", "987654321", 1L, 13L, "OTRO", "Freddy10002@email.com", "ACTIVO"},
                {"Jhan", "Carlos", "Malca", "Hernández", "Contacto de Jhan Malca", "Dirección Jhan Malca", "DNI", "75438287", "2002-12-21", "961605350", "961605350", 1L, 7L, "OTRO", "Jhan10003@email.com", "ACTIVO"},
                {"Henry", "", "Mendoza", "Mestanza", "Contacto de Henry Mendoza", "Dirección Henry Mendoza", "DNI", "43956768", "2000-01-01", "987654321", "987654321", 1L, 6L, "OTRO", "Henry10004@email.com", "ACTIVO"},
                {"Miguel", "Angel", "Flores", "Rojas", "Contacto de Miguel Flores", "Dirección Miguel Flores", "DNI", "74024950", "2000-01-01", "987654321", "987654321", 1L, 2L, "OTRO", "Miguel10005@email.com", "ACTIVO"},
                {"Luz", "Delia", "Condori", "Vilca", "Contacto de Luz Condori", "Dirección Luz Condori", "DNI", "70074349", "2000-01-01", "990554377", "990554377", 2L, 18L, "OTRO", "Luz10006@email.com", "ACTIVO"},
                {"Jhonel", "Kimoshi", "Orillo", "Bernales", "Contacto de Jhonel Orillo", "Dirección Jhonel Orillo", "DNI", "76334780", "1999-03-27", "924204075", "924204075", 1L, 8L, "OTRO", "Jhonel10007@email.com", "ACTIVO"},
                {"Jaime", "Nazario", "Chura", "Arias", "Contacto de Jaime Chura", "Dirección Jaime Chura", "DNI", "01323787", "2000-01-01", "987654321", "987654321", 1L, 8L, "OTRO", "Jaime10008@email.com", "ACTIVO"},
                {"Maryluz", "", "Sarzozo", "Mendoza", "Contacto de Maryluz Sarzozo", "Dirección Maryluz Sarzozo", "DNI", "40741399", "1980-07-26", "927413373", "927413373", 2L, 8L, "OTRO", "Maryluz10009@email.com", "ACTIVO"},
                {"Luis", "Alberto", "Pinado", "Anchela", "Contacto de Luis Pinado", "Dirección Luis Pinado", "DNI", "73630887", "1994-07-12", "994319031", "994319031", 1L, 7L, "OTRO", "Luis10010@email.com", "ACTIVO"},
                {"Vanessa", "", "Mamani", "Chura", "Contacto de Vanessa Mamani", "Dirección Vanessa Mamani", "DNI", "70341219", "2000-01-01", "987654321", "987654321", 2L, 10L, "OTRO", "Vanessa10011@email.com", "ACTIVO"},
                {"Luis", "", "Linares", "Velazco", "Contacto de Luis Linares", "Dirección Luis Linares", "DNI", "48200538", "1994-03-28", "978447648", "978447648", 1L, 7L, "OTRO", "Luis10012@email.com", "ACTIVO"},
                {"Edison", "Jhon", "Ordoñez", "Gonzales", "Contacto de Edison Ordoñez", "Dirección Edison Ordoñez", "DNI", "70088013", "1998-04-25", "994319031", "994319031", 1L, 3L, "OTRO", "Edison10013@email.com", "ACTIVO"},
                {"Nahomi", "Sheyla", "Burgos", "Robles", "Contacto de Nahomi Burgos", "Dirección Nahomi Burgos", "DNI", "73737885", "2000-01-01", "987654321", "987654321", 2L, 9L, "OTRO", "Nahomi10014@email.com", "ACTIVO"},
                {"Rosmel", "", "Mendoza", "Rojas", "Contacto de Rosmel Mendoza", "Dirección Rosmel Mendoza", "DNI", "46174989", "2000-01-01", "987654321", "987654321", 1L, 5L, "OTRO", "Rosmel10015@email.com", "ACTIVO"},
                {"Hobilder", "", "Medina", "Mendoza", "Contacto de Hobilder Medina", "Dirección Hobilder Medina", "DNI", "75757143", "2000-08-05", "950399409", "950399409", 1L, 7L, "OTRO", "Hobilder10016@email.com", "ACTIVO"},
                {"Pedro", "", "Saldivar", "Ramires", "Contacto de Pedro Saldivar", "Dirección Pedro Saldivar", "DNI", "27850053", "2000-01-01", "942424476", "942424476", 1L, 7L, "OTRO", "Pedro10017@email.com", "ACTIVO"},
                {"Jesus", "", "Requejo", "Cueva", "Contacto de Jesus Requejo", "Dirección Jesus Requejo", "DNI", "45740759", "2000-01-01", "987654321", "987654321", 1L, 19L, "OTRO", "Jesus10018@email.com", "ACTIVO"},
                {"Moises", "", "Rodriguez", "Cabrera", "Contacto de Moises Rodriguez", "Dirección Moises Rodriguez", "DNI", "45058837", "2000-01-01", "987654321", "987654321", 1L, 22L, "OTRO", "Moises10019@email.com", "ACTIVO"},
                {"Dayslee", "Daly", "Shica", "Verástegui", "Contacto de Dayslee Shica", "Dirección Dayslee Shica", "DNI", "48488303", "1994-01-29", "934944894", "934944894", 2L, 10L, "OTRO", "Dayslee10020@email.com", "ACTIVO"},
                {"Amanda", "Eliodora", "Perez", "Medina", "Contacto de Amanda Perez", "Dirección Amanda Perez", "DNI", "21114798", "2000-01-01", "987654321", "987654321", 2L, 12L, "OTRO", "Amanda10021@email.com", "ACTIVO"},
                {"Jessica", "Mercedes", "Gonzales", "Amaya", "Contacto de Jessica Gonzales", "Dirección Jessica Gonzales", "DNI", "45336036", "1989-09-22", "954184089", "954184089", 2L, 8L, "OTRO", "Jessica10022@email.com", "ACTIVO"},
                {"Lucero", "Fatima", "Santiago", "Huaman", "Contacto de Lucero Santiago", "Dirección Lucero Santiago", "DNI", "70876250", "1992-11-06", "987054356", "987054356", 2L, 23L, "OTRO", "Lucero10023@email.com", "ACTIVO"},
                {"Suryam", "Liya", "Nuñez", "Pallara", "Contacto de Suryam Nuñez", "Dirección Suryam Nuñez", "DNI", "72016158", "1998-04-18", "918420581", "918420581", 2L, 11L, "OTRO", "Suryam10024@email.com", "ACTIVO"},
                {"Patricia", "Alexia", "Paiva", "Cavero", "Contacto de Patricia Paiva", "Dirección Patricia Paiva", "DNI", "76909633", "2003-05-22", "900795609", "900795609", 2L, 4L, "OTRO", "Patricia10025@email.com", "ACTIVO"},
                {"Sarita", "Colonia", "Mayhuiri", "Aroni", "Contacto de Sarita Mayhuiri", "Dirección Sarita Mayhuiri", "DNI", "73812975", "2001-05-23", "953568147", "953568147", 2L, 11L, "OTRO", "Sarita10026@email.com", "ACTIVO"},
                {"Teodora", "", "Carhuallanqui", "Iparraguirre", "Contacto de Teodora Carhuallanqui", "Dirección Teodora Carhuallanqui", "DNI", "10169960", "1973-03-12", "987654321", "987654321", 2L, 12L, "OTRO", "Teodora10027@email.com", "ACTIVO"},
                {"Yerson", "Ricardo", "Callupe", "Arzapalo", "Contacto de Yerson Callupe", "Dirección Yerson Callupe", "DNI", "75969689", "2003-09-07", "986647900", "986647900", 1L, 8L, "OTRO", "Yerson10028@email.com", "ACTIVO"},
                {"Alberto", "Ramon", "Belizario", "Ticona", "Contacto de Alberto Belizario", "Dirección Alberto Belizario", "DNI", "48525794", "1995-01-23", "933862921", "933862921", 1L, 13L, "OTRO", "Alberto10029@email.com", "ACTIVO"},
                {"Jhon", "Anthony", "Mendoza", "Paz", "Contacto de Jhon Mendoza", "Dirección Jhon Mendoza", "DNI", "74897057", "1998-04-21", "935037033", "935037033", 1L, 8L, "OTRO", "Jhon10030@email.com", "ACTIVO"},
                {"Daniel", "Alejandro", "Dávila", "Torres", "Contacto de Daniel Dávila", "Dirección Daniel Dávila", "DNI", "45000221", "1988-03-28", "957565571", "957565571", 1L, 5L, "OTRO", "Daniel10031@email.com", "ACTIVO"},
                {"Beatriz", "Feliciana", "Rodriguez", "Triveño", "Contacto de Beatriz Rodriguez", "Dirección Beatriz Rodriguez", "DNI", "09726705", "1970-05-18", "959204070", "959204070", 2L, 12L, "OTRO", "Beatriz10032@email.com", "ACTIVO"},
                {"Mery", "Rosario", "Retuerto", "Castro", "Contacto de Mery Retuerto", "Dirección Mery Retuerto", "DNI", "70213105", "2000-01-01", "987654321", "987654321", 2L, 15L, "OTRO", "Mery10033@email.com", "ACTIVO"},
                {"Erik", "Daniel", "Orillo", "Bernales", "Contacto de Erik Orillo", "Dirección Erik Orillo", "DNI", "76635083", "2000-01-01", "987654321", "987654321", 1L, 21L, "OTRO", "Erik10034@email.com", "ACTIVO"},
                {"Sabina", "", "Rodriguez", "Isquierdo", "Contacto de Sabina Rodriguez", "Dirección Sabina Rodriguez", "DNI", "43794627", "2000-01-01", "987654321", "987654321", 2L, 18L, "OTRO", "Sabina10035@email.com", "ACTIVO"},
                {"Erika", "Vanessa", "Orellano", "Gonzales", "Contacto de Erika Orellano", "Dirección Erika Orellano", "DNI", "45202685", "1988-03-13", "987654321", "987654321", 2L, 16L, "OTRO", "Erika10036@email.com", "ACTIVO"},
                {"Luz", "Esther", "Mamani", "Pampamallco", "Contacto de Luz Mamani", "Dirección Luz Mamani", "DNI", "47532154", "2000-01-01", "987654321", "987654321", 2L, 18L, "OTRO", "Luz10037@email.com", "ACTIVO"},
                {"Geraldy", "Neudy", "Ramos", "Vasquez", "Contacto de Geraldy Ramos", "Dirección Geraldy Ramos", "DNI", "77234816", "2002-01-21", "976439265", "976439265", 2L, 11L, "OTRO", "Geraldy10038@email.com", "ACTIVO"},
                {"Luis", "David", "Celestino", "Torres", "Contacto de Luis Celestino", "Dirección Luis Celestino", "DNI", "21121992", "1970-12-15", "987654321", "987654321", 1L, 6L, "OTRO", "Luis10039@email.com", "ACTIVO"},
                {"Edison", "Abrhan", "Mattire", "Peralta", "Contacto de Edison Mattire", "Dirección Edison Mattire", "DNI", "60356087", "2000-12-22", "930929335", "930929335", 1L, 8L, "OTRO", "Edison10040@email.com", "ACTIVO"},
                {"Josue", "Alfredo", "Bautista", "Carlos", "Contacto de Josue Bautista", "Dirección Josue Bautista", "DNI", "76122076", "2000-01-01", "987654321", "987654321", 1L, 24L, "OTRO", "Josue10041@email.com", "ACTIVO"},
                {"Mercedes", "Sussy", "Tinoco", "Aguilar", "Contacto de Mercedes Tinoco", "Dirección Mercedes Tinoco", "DNI", "09729763", "2000-01-01", "987654321", "987654321", 2L, 12L, "OTRO", "Mercedes10042@email.com", "ACTIVO"},
                {"Milagros", "Maricsa", "Gonzales", "Gonzales", "Contacto de Milagros Gonzales", "Dirección Milagros Gonzales", "DNI", "40892772", "1981-05-30", "981226370", "981226370", 2L, 7L, "OTRO", "Milagros10043@email.com", "ACTIVO"},
                {"Wilfredo", "Crisostomo", "Mattire", "Peralta", "Contacto de Wilfredo Mattire", "Dirección Wilfredo Mattire", "DNI", "60356086", "1998-12-04", "987654321", "987654321", 1L, 8L, "OTRO", "Wilfredo10044@email.com", "ACTIVO"},
                {"Antoni", "", "Saldivar", "Gonzales", "Contacto de Antoni Saldivar", "Dirección Antoni Saldivar", "DNI", "73248201", "2000-01-01", "987654321", "987654321", 1L, 7L, "OTRO", "Antoni10045@email.com", "ACTIVO"},
                {"Danfer", "Americo", "Salazar", "Pobes", "Contacto de Danfer Salazar", "Dirección Danfer Salazar", "DNI", "72241228", "2005-01-09", "913122267", "913122267", 1L, 8L, "OTRO", "Danfer10046@email.com", "ACTIVO"},
                {"Denilson", "Rubinho", "Tacuche", "Pumacayo", "Contacto de Denilson Tacuche", "Dirección Denilson Tacuche", "DNI", "73327929", "2005-05-20", "952473637", "952473637", 1L, 7L, "OTRO", "Denilson10047@email.com", "INACTIVO"},
                {"Alexander", "", "Padilla", "Quispe", "Contacto de Alexander Padilla", "Dirección Alexander Padilla", "DNI", "42304729", "1981-07-21", "935763594", "935763594", 1L, 7L, "OTRO", "Alexander10048@email.com", "INACTIVO"},
                {"Vossler", "Jezreel Moises", "Cabrera", "Pizarro", "Contacto de Vossler Cabrera", "Dirección Vossler Cabrera", "DNI", "42823078", "1984-11-29", "921480289", "921480289", 1L, 7L, "OTRO", "Vossler10049@email.com", "INACTIVO"},
                {"Elmer", "Josue", "Peralta", "Santos", "Contacto de Elmer Peralta", "Dirección Elmer Peralta", "DNI", "60356090", "2003-12-05", "907517752", "907517752", 1L, 7L, "OTRO", "Elmer10050@email.com", "INACTIVO"},
                {"Anthuan", "Rey Set", "Robles", "Heredia", "Contacto de Anthuan Robles", "Dirección Anthuan Robles", "DNI", "73136439", "2001-05-15", "901198646", "901198646", 1L, 7L, "OTRO", "Anthuan10051@email.com", "INACTIVO"},
                {"Daniel", "Eudoro", "Chimbo", "Bellasmin", "Contacto de Daniel Chimbo", "Dirección Daniel Chimbo", "DNI", "74812965", "2001-08-15", "947936158", "947936158", 1L, 7L, "OTRO", "Daniel10052@email.com", "INACTIVO"},
                {"Rodrigo", "", "Maquera", "Sarzozo", "Contacto de Rodrigo Maquera", "Dirección Rodrigo Maquera", "DNI", "73643131", "2006-01-16", "926592375", "926592375", 1L, 7L, "OTRO", "Rodrigo10053@email.com", "INACTIVO"},
                {"Jhordan", "Percy", "Sarzozo", "Pari", "Contacto de Jhordan Sarzozo", "Dirección Jhordan Sarzozo", "DNI", "60581182", "2006-01-16", "910444098", "910444098", 1L, 7L, "OTRO", "Jhordan10054@email.com", "INACTIVO"},
                {"Nilton", "Saúl", "Hinostroza", "Oscuvilca", "Contacto de Nilton Hinostroza", "Dirección Nilton Hinostroza", "DNI", "72243710", "2005-05-11", "936371879", "936371879", 1L, 7L, "OTRO", "Nilton10055@email.com", "INACTIVO"},
                {"Nolverto", "Ronaldiño", "Reyes", "Perez", "Contacto de Nolverto Reyes", "Dirección Nolverto Reyes", "DNI", "74284158", "1997-12-27", "948417813", "948417813", 1L, 8L, "OTRO", "Nolverto10056@email.com", "INACTIVO"}


        };

        List<Trabajador> trabajadoresAGuardar = new ArrayList<>();

        // 1. BUCLE DE LECTURA Y CONSTRUCCIÓN
        for (Object[] row : trabajadoresData) {
            // Buscamos las relaciones (Idealmente deberías manejar excepciones aquí si no existen)
            Genero genero = generoRepository.findById(((Number) row[11]).intValue()).orElse(null);
            Puesto puesto = puestoRepository.findById(((Number) row[12]).intValue()).orElse(null);

            Trabajador trabajador = Trabajador.builder()
                    .pNombre((String) row[0])
                    .sNombre((String) row[1])
                    .aPaterno((String) row[2])
                    .aMaterno((String) row[3])
                    .contactoEmergencias((String) row[4])
                    .direccion((String) row[5])
                    .docIdentidad((String) row[6])
                    .nroDocumento((String) row[7])
                    .fechaNac(LocalDate.parse((String) row[8])) // Asegúrate que el formato sea YYYY-MM-DD
                    .nroContacto((String) row[9])
                    .telefono((String) row[10])
                    .genero(genero)
                    .puesto(puesto)
                    .parentesco(Parentesco.valueOf((String) row[13]))
                    .email((String) row[14])
                    .estado(EstadoTrabajador.valueOf((String) row[15]))
                    .build();

            // CASCADE: Creamos el usuario y lo asignamos al trabajador antes de guardar
            Usuario usuario = Usuario.builder()
                    .username(trabajador.getEmail())
                    .password(passwordEncoder.encode(trabajador.getNroDocumento())) // DNI hasheado
                    .rol("ROLE_TRABAJADOR")
                    .trabajador(trabajador)
                    .enabled(trabajador.getEstado() == EstadoTrabajador.ACTIVO)
                    .build();

            trabajador.setUsuario(usuario); // Gracias a CascadeType.ALL en tu entidad, esto guardará ambos
            trabajadoresAGuardar.add(trabajador);
        }

        // 2. GUARDADO MASIVO (Más eficiente para la base de datos)
        trabajadorRepository.saveAll(trabajadoresAGuardar);
    }

    private void crearSuperAdmin() {
        if (usuarioRepository.findByUsername("administracion@avendacom.com").isEmpty()) {
            Usuario admin = Usuario.builder()
                    .username("administracion@avendacom.com")
                    .password(passwordEncoder.encode("123456"))
                    .rol("ROLE_SUPERADMIN")
                    .enabled(true)
                    .build();
            usuarioRepository.save(admin);
        }
    }

}