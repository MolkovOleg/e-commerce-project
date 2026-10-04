package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.dto.*;
import com.ecommerce.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Profile Controller", description = "Endpoints for managing user profiles, deliver addresses and roles")
public class UserController {

    private final UserService userService;

    @GetMapping("/{profileId}")
    @Operation(summary = "Get user profile by ID (UUID)")
    public ResponseEntity<UserProfileResponse> getUserProfile(
            @PathVariable UUID profileId) {
        return ResponseEntity.ok(userService.getProfile(profileId));
    }

    @PutMapping("/{profileId}")
    @Operation(summary = "Update user profile details (first name, last name, phone)")
    public ResponseEntity<UserProfileResponse> updateUserProfile(
            @PathVariable UUID profileId,
            @Valid @RequestBody UpdateUserProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(profileId, request));
    }

    @GetMapping("/{profileId}/addresses")
    @Operation(summary = "Get all delivery addresses for a user")
    public ResponseEntity<List<AddressResponse>> getAddresses(
            @PathVariable UUID profileId) {
        return ResponseEntity.ok(userService.getAddresses(profileId));
    }

    @PostMapping("/{profileId}/addresses")
    @Operation(summary = "Add a new delivery address")
    public ResponseEntity<AddressResponse> addAddress(
            @PathVariable UUID profileId,
            @Valid @RequestBody AddressRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(userService.addAddress(profileId, request));
    }

    @DeleteMapping("/{profileId}/addresses/{addressId}")
    @Operation(summary = "Delete delivery address")
    public ResponseEntity<Void> deleteAddress(
            @PathVariable UUID profileId,
            @PathVariable UUID addressId) {

        userService.deleteAddress(addressId, profileId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{profileId}/roles")
    @Operation(summary = "Update roles for user profile")
    public ResponseEntity<UserProfileResponse> updateRoles(
            @PathVariable UUID profileId,
            @Valid @RequestBody UpdateRolesRequest request) {

        return ResponseEntity.ok(userService.updateRoles(profileId, request));
    }
}
