package com.idat.asistencia.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    /**
     * Redes desde las que se acepta la marcacion, separadas por coma.
     * Se configura en application.properties como
     * asistencia.lector.ips-permitidas.
     */
    @Value("${asistencia.lector.ips-permitidas:127.0.0.1/32,::1/128}")
    private String ipsPermitidas;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        List<IpAddressMatcher> matchers = Arrays.stream(ipsPermitidas.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(IpAddressMatcher::new)
                .toList();

        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, e) -> {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.setContentType("application/json");
                res.getWriter().write(
                        "{\"status\":401,\"message\":\"Sesion expirada. Inicia sesion nuevamente.\"}");
            }))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/login").permitAll()
                    .requestMatchers("/api/auth/recuperar-password").permitAll()

                    // ---- Punto de marcacion (RS-05) ----
                    // Sin sesion de usuario, por tratarse del dispositivo
                    // fisico de planta, pero restringido a las IP de la
                    // red de la empresa.
                    //
                    // En el prototipo estos endpoints eran permitAll sin
                    // restriccion alguna: como ademas el codigo de barras
                    // es el identificador correlativo del trabajador,
                    // bastaba con probar numeros desde cualquier red que
                    // alcanzara el servidor.
                    .requestMatchers("/api/asistencia/marcar",
                                     "/api/asistencia/en-planta-publica")
                        .access((authentication, context) -> {
                            boolean permitido = matchers.stream()
                                    .anyMatch(m -> m.matches(context.getRequest()));
                            return new org.springframework.security.authorization
                                    .AuthorizationDecision(permitido);
                        })

                    .requestMatchers("/api/maestros/generos").permitAll()
                    .requestMatchers("/api/maestros/areas").permitAll()
                    .requestMatchers("/api/maestros/areas/*/puestos").permitAll()
                    .requestMatchers("/error").permitAll()
                    .anyRequest().authenticated()
            )
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
