package com.ProdeMaster.UserServices.Security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {

    private final String secretKey;

    private final long expirationTime;

    public JwtUtil(@Value("${jwt.secret}") String secretKey,
                   @Value("${jwt.expiration}") long expirationTime) {
        this.secretKey = secretKey;
        this.expirationTime = expirationTime;
    }

    /**
     * Genera un token JWT para un usuario dado.
     * @param username Nombre de usuario a incluir en el token.
     * @return Token JWT generado.
     */
    public String generateToken(String username) {
        return JWT.create()
                .withSubject(username) // Se establece el sujeto (usuario)
                .withIssuedAt(new Date()) // Fecha de emisión
                .withExpiresAt(new Date(System.currentTimeMillis() + expirationTime)) // Fecha de expiración
                .sign(Algorithm.HMAC256(secretKey)); // Firma con HMAC y la clave secreta
    }

    /**
     * Valida un token JWT y retorna el nombre de usuario si es válido.
     * @param token Token a validar.
     * @return Nombre de usuario contenido en el token.
     * @throws JWTVerificationException Si el token es inválido o ha expirado.
     */
    public String validateToken(String token) {
        try {
            JWTVerifier verifier = JWT.require(Algorithm.HMAC256(secretKey))
                    .build();

            DecodedJWT jwt = verifier.verify(token);
            return jwt.getSubject(); // Retorna el username del token
        } catch (JWTVerificationException e) {
            throw new RuntimeException("Token inválido o expirado", e);
        }
    }
}
