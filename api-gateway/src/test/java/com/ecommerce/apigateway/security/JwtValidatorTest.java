package com.ecommerce.apigateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtValidatorTest {

    private static final String SECRET = "kgy0iUhUm9BkRXwFrw8ksMpP5TmT3tE/o92K2Kjj6ys=";
    private JwtValidator jwtValidator;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        jwtValidator = new JwtValidator(SECRET);
        byte[] keyBytes = Base64.getDecoder().decode(SECRET);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    @Test
    @DisplayName("Валидный токен возвращает true")
    void testValidTokenReturnsTrue() {
        String token = Jwts.builder()
                .subject("test-user")
                .claim("userId", "12345")
                .claim("roles", List.of("ROLE_USER"))
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(signingKey)
                .compact();

        assertThat(jwtValidator.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("Истекший токен возвращает false")
    void testExpiredTokenReturnsFalse() {
        String token = Jwts.builder()
                .subject("test-user")
                .claim("userId", "12345")
                .claim("roles", List.of("ROLE_USER"))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(signingKey)
                .compact();

        assertThat(jwtValidator.isTokenValid(token)).isFalse();
    }

    @Test
    @DisplayName("Извлечение userId и ролей из токена")
    void testExtractClaims() {
        String token = Jwts.builder()
                .subject("test-user")
                .claim("userId", "user-uuid-12345")
                .claim("roles", List.of("ROLE_USER, ROLE_ADMIN"))
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(signingKey)
                .compact();

        assertThat(jwtValidator.extractUserId(token)).isEqualTo("user-uuid-12345");
        assertThat(jwtValidator.extractRoles(token)).containsExactly("ROLE_USER, ROLE_ADMIN");
    }



}
