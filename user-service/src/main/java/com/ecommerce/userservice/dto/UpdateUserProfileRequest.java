package com.ecommerce.userservice.dto;

import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @Size(max = 100, message = "First name must not exceed 100 characters")
        String firstName,

        @Size(max = 100, message = "Last name must not exceed 100 characters")
        String lastName,

        @Size(max = 30, message = "Phone must not exceed 30 characters")
        String phone
) {
}
