package com.ecommerce.userservice.dto;

import com.ecommerce.userservice.domain.UserProfileStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Builder
public record UserProfileResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phone,
        UserProfileStatus status,
        Set<String> roles,
        List<AddressResponse> addresses,
        Instant createdAt,
        Instant updatedAt
) {
}
