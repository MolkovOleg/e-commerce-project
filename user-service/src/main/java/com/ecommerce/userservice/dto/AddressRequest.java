package com.ecommerce.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank(message = "Country is required")
        @Size(max = 100)
        String country,

        @NotBlank(message = "City is required")
        @Size(max = 100)
        String city,

        @NotBlank(message = "Street is required")
        @Size(max = 255)
        String street,

        @NotBlank(message = "Building is required")
        @Size(max = 20)
        String building,

        @Size(max = 20)
        String apartment,

        @Size(max = 20)
        String zipCode,

        boolean isDefault
) {
}
