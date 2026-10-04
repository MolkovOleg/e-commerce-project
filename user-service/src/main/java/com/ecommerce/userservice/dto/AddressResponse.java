package com.ecommerce.userservice.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record AddressResponse(
        UUID id,
        String country,
        String city,
        String street,
        String building,
        String apartment,
        String zipCode,
        boolean isDefault
) {
}
