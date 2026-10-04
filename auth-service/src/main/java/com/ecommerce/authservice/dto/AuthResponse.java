package com.ecommerce.authservice.dto;

import lombok.Builder;

@Builder
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInMs
) {
}
