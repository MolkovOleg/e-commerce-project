package com.ecommerce.authservice.service;

import com.ecommerce.authservice.config.JwtConfig;
import com.ecommerce.authservice.domain.RefreshToken;
import com.ecommerce.authservice.domain.Role;
import com.ecommerce.authservice.domain.User;
import com.ecommerce.authservice.domain.UserStatus;
import com.ecommerce.authservice.dto.AuthResponse;
import com.ecommerce.authservice.dto.LoginRequest;
import com.ecommerce.authservice.dto.RefreshTokenRequest;
import com.ecommerce.authservice.dto.RegisterRequest;
import com.ecommerce.authservice.repository.RefreshTokenRepository;
import com.ecommerce.authservice.repository.UserRepository;
import com.ecommerce.authservice.security.JwtService;
import com.ecommerce.common.event.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String USER_CREATED_TOPIC = "user.created";
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtConfig jwtConfig;
    private final AuthenticationManager authenticationManager;
    private final KafkaTemplate<String, UserCreatedEvent> kafkaTemplate;


    /**
     *************************************
     * Основные public методы Auth Service
     *************************************
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("User with email " + request.email() + " already exists");
        }

        // Сохранение учетных данных пользователя в базу данных
        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(UserStatus.ACTIVE)
                .roles(Set.of(Role.ROLE_USER))
                .build();

        User savedUser = userRepository.save(user);

        // Отправка Avro-события через Kafka для сервиса user-service
        sendUserCreatedEvent(savedUser);

        // Создание пары токенов access + refresh
        return generateAuthResponse(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Проверяем логин и пароль через Spring Security AuthenticationManager
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        return generateAuthResponse(user);
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(request.refreshToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (token.isRevoked() || token.isExpired()) {
            throw new IllegalArgumentException("Refresh token is revoked or expired");
        }

        token.setRevoked(true);

        return generateAuthResponse(token.getUser());
    }

    /**
     *********************************************
     * Вспомогательные private методы Auth Service
     *********************************************
     */
    private void sendUserCreatedEvent(User user) {
        // Создание события по Avro шаблону
        UserCreatedEvent event = UserCreatedEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setUserId(user.getId().toString())
                .setEmail(user.getEmail())
                .setRoles(user.getRoles().stream().map(Enum::name).toList())
                .setCreatedAt(Instant.now())
                .build();

        // Отправка события по Kafka с логированием статуса
        kafkaTemplate.send(USER_CREATED_TOPIC, user.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send UserCreatedEvent for user {}: {}",
                                user.getId(), ex.getMessage());
                    } else {
                        log.info("Successfully send UserCreatedEvent for user {} to {}",
                                user.getId(), USER_CREATED_TOPIC);
                    }
                });
    }

    private AuthResponse generateAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        RefreshToken refreshToken = createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getTokenHash())
                .tokenType("Bearer")
                .expiresInMs(jwtConfig.getAccessTokenExpirationMs())
                .build();
    }

    private RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(UUID.randomUUID().toString())
                .expiresAt(Instant.now().plusMillis(jwtConfig.getRefreshTokenExpirationMs()))
                .revoked(false)
                .build();
        return refreshTokenRepository.save(refreshToken);
    }
}
