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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private JwtConfig jwtConfig;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private KafkaTemplate<String, UserCreatedEvent> kafkaTemplate;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("Успешная регистрация пользователя")
    void testRegister_Successful_AuthResponse() {
        RegisterRequest request = new RegisterRequest(
                "test@example.com",
                "Password123!");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hashed_pass");

        UUID userId = UUID.randomUUID();
        User savedUser = User.builder()
                .id(userId)
                .email("test@example.com")
                .passwordHash("hashed_pass")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(Role.ROLE_USER))
                .build();

        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access_token_mock");
        when(jwtConfig.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access_token_mock");
        assertThat(response.refreshToken()).isNotBlank();
        verify(kafkaTemplate).send(eq("user.created"), eq(userId.toString()), any(UserCreatedEvent.class));
    }

    @Test
    @DisplayName("Регистрация с существующим email выбрасывает исключение")
    void testRegister_EmailAlreadyExists_ThrowsException() {
        RegisterRequest request = new RegisterRequest(
                "existing@example.com",
                "Password123!");

        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User with email " + request.email() + " already exists");
    }

    @Test
    @DisplayName("Успешный вход (login)")
    void testLogin_Successful_AuthResponse() {
        LoginRequest request = new LoginRequest(
                "test@example.com",
                "Password123!");

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .roles(Set.of(Role.ROLE_USER))
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("access_token_mock");
        when(jwtConfig.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access_token_mock");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("Вход с неверным паролем выбрасывает исключение")
    void testLogin_BadCredentials_ThrowsException() {
        LoginRequest request = new LoginRequest(
                "test@example.com",
                "WrongPassword");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("Успешное обновление токена")
    void testRefreshToken_Successful() {
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .email("test@example.com")
                .roles(Set.of(Role.ROLE_USER))
                .build();

        RefreshToken oldToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .tokenHash("old_refresh_token")
                .user(user)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash("old_refresh_token")).thenReturn(Optional.of(oldToken));
        when(jwtService.generateAccessToken(user)).thenReturn("new_access_token");
        when(jwtConfig.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenRequest request = new RefreshTokenRequest("old_refresh_token");
        AuthResponse response = authService.refreshToken(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("new_access_token");
        assertThat(oldToken.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("Обновление отозванного или просроченного токена выбрасывает исключение")
    void testRefreshToken_RevokedOrExpired_ThrowsException() {
        RefreshToken revokedToken = RefreshToken.builder()
                .tokenHash("revoked_token")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(true)
                .build();

        when(refreshTokenRepository.findByTokenHash("revoked_token")).thenReturn(Optional.of(revokedToken));

        RefreshTokenRequest request = new RefreshTokenRequest("revoked_token");

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("revoked or expired");
    }


}
