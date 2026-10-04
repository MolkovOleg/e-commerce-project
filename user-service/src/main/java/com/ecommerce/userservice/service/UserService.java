package com.ecommerce.userservice.service;

import com.ecommerce.userservice.domain.UserAddress;
import com.ecommerce.userservice.domain.UserProfile;
import com.ecommerce.userservice.dto.*;
import com.ecommerce.userservice.repository.UserAddressRepository;
import com.ecommerce.userservice.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserProfileRepository userProfileRepository;
    private final UserAddressRepository userAddressRepository;


    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID profileId) {
        UserProfile profile = findProfileOrThrow(profileId);
        return mapToProfileResponse(profile);
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID profileId, UpdateUserProfileRequest request) {
        UserProfile profile = findProfileOrThrow(profileId);

        profile.setFirstName(request.firstName());
        profile.setLastName(request.lastName());
        profile.setPhone(request.phone());

        log.info("Updated profile details for userId={}", profileId);
        return mapToProfileResponse(profile);
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(UUID profileId) {
        findProfileOrThrow(profileId);
        return userAddressRepository.findAllByUserProfileId(profileId).stream()
                .map(this::mapToAddressResponse)
                .toList();
    }

    @Transactional
    public AddressResponse addAddress(UUID profileId, AddressRequest request) {
        UserProfile profile = findProfileOrThrow(profileId);

        UserAddress address = UserAddress.builder()
                .country(request.country())
                .city(request.city())
                .street(request.street())
                .building(request.building())
                .apartment(request.apartment())
                .zipCode(request.zipCode())
                .isDefault(request.isDefault())
                .build();

        profile.addAddress(address);
        UserAddress savedAddress = userAddressRepository.save(address);

        log.info("Added address for userId={}", profileId);
        return mapToAddressResponse(savedAddress);
    }

    @Transactional
    public void deleteAddress(UUID addressId, UUID profileId) {
        UserAddress address = userAddressRepository.findByIdAndUserProfileId(addressId, profileId).
                orElseThrow(() ->
                        new NoSuchElementException("Address not found with id=" + addressId + " for user=" + profileId));

        UserProfile profile = address.getUserProfile();
        profile.removeAddress(address);
        userAddressRepository.delete(address);

        log.info("Deleted address id={} for userId={}", addressId,  profileId);
    }

    @Transactional
    public UserProfileResponse updateRoles(UUID profileId, UpdateRolesRequest request) {
        UserProfile profile = findProfileOrThrow(profileId);
        profile.setRoles(request.roles());

        log.info("Updated roles for userId={}", profileId);
        return mapToProfileResponse(profile);
    }


    private UserProfile findProfileOrThrow(UUID profileId) {
        return userProfileRepository.findById(profileId)
                .orElseThrow(() -> new NoSuchElementException("User profile not found with id=" + profileId));
    }

    private UserProfileResponse mapToProfileResponse(UserProfile profile) {
        List<AddressResponse> addressResponses = profile.getAddresses() != null
                ? profile.getAddresses().stream()
                    .map(this::mapToAddressResponse).toList()
                : List.of();

        return UserProfileResponse.builder()
                .id(profile.getId())
                .email(profile.getEmail())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .phone(profile.getPhone())
                .status(profile.getStatus())
                .roles(profile.getRoles())
                .addresses(addressResponses)
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private AddressResponse mapToAddressResponse(UserAddress address) {
        return AddressResponse.builder()
                .id(address.getId())
                .country(address.getCountry())
                .city(address.getCity())
                .street(address.getStreet())
                .building(address.getBuilding())
                .apartment(address.getApartment())
                .zipCode(address.getZipCode())
                .isDefault(address.isDefault())
                .build();
    }
}
