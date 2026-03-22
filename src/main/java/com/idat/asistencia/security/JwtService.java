package com.idat.asistencia.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {
    // Llave secreta estática para pruebas
    private static final String SECRET_KEY = "VGhpcy1pcy1hLXZlcnktc2VjdXJlLWtleS1mb3Itand0LXRva2Vucy1pbi1zcHJpbmctYm9vdC1wcm9qZWN0";

    // =================================================================
    // CÓDIGO ACTUALIZADO: Inyectamos el rol en los Claims del token
    // =================================================================
    public String generateToken(UserDetails userDetails) {
        // Obtenemos el rol de los Authorities de Spring Security
        String rol = userDetails.getAuthorities().iterator().next().getAuthority();

        return Jwts.builder()
                .claim("rol", rol) // <-- METEMOS EL ROL AQUÍ
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                //.expiration(new Date(System.currentTimeMillis() + 1000 * 8)) // 45 segundos
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24)) // 24 horas
                .signWith(getSignInKey())
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}