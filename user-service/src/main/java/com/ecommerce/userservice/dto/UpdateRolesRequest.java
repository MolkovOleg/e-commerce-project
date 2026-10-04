package com.ecommerce.userservice.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UpdateRolesRequest(
        @NotEmpty(message = "Roles set cannot be empty")
        Set<String> roles
) {
}
