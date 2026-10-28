package com.ecommerce.userservice.service;

import com.ecommerce.userservice.domain.UserAddress;
import com.ecommerce.userservice.domain.UserProfile;
import com.ecommerce.userservice.domain.UserProfileStatus;
import com.ecommerce.userservice.dto.AddressRequest;
import com.ecommerce.userservice.dto.AddressResponse;
import com.ecommerce.userservice.dto.UpdateRolesRequest;
import com.ecommerce.userservice.dto.UpdateUserProfileRequest;
import com.ecommerce.userservice.dto.UserProfileResponse;
import com.ecommerce.userservice.repository.UserAddressRepository;
import com.ecommerce.userservice.repository.UserProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private UserAddressRepository userAddressRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("Успешное получение профиля по ID")
    void testGetUserProfileByIdSuccessfulReturnsUserProfileResponse() {
        UUID userId = UUID.randomUUID();

        UserProfile profile = UserProfile.builder()
                .id(userId)
                .email("user@example.com")
                .firstName("firstName")
                .lastName("lastName")
                .phone("123456789")
                .status(UserProfileStatus.ACTIVE)
                .roles(Set.of("ROLE_USER"))
                .build();

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));

        UserProfileResponse response = userService.getProfile(userId);

        assertThat(response.id()).isEqualTo(userId);
    }

    @Test
    @DisplayName("Профиль с данным ID не найден")
    void testGetUserProfileByIdNotExistsReturnsNoSuchElementException() {
        UUID randomId = UUID.randomUUID();

        when(userProfileRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(randomId))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("User profile not found with id=" + randomId);
    }

    @Test
    @DisplayName("Обновление данных профиля")
    void testUpdateUserProfileSuccessfulReturnsUserProfileResponse() {
        UUID userId = UUID.randomUUID();

        UserProfile profile = UserProfile.builder()
                .id(userId)
                .email("user@example.com")
                .firstName("Old")
                .lastName("Name")
                .build();

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));

        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                "New",
                "Surname",
                "+79998887766"
        );

        UserProfileResponse response = userService.updateProfile(userId, request);

        assertThat(response.firstName()).isEqualTo("New");
        assertThat(response.lastName()).isEqualTo("Surname");
        assertThat(response.phone()).isEqualTo("+79998887766");
    }

    @Test
    @DisplayName("Получение списка адресов пользователя")
    void testGetAddressesSuccessfulReturnsAddressResponse() {
        UUID userId = UUID.randomUUID();

        UserProfile profile = UserProfile.builder()
                .id(userId)
                .build();

        UserAddress address = UserAddress.builder()
                .id(UUID.randomUUID())
                .userProfile(profile)
                .city("Moscow")
                .street("Tverskaya")
                .building("1")
                .isDefault(true)
                .build();

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(userAddressRepository.findAllByUserProfileId(userId)).thenReturn(List.of(address));

        List<AddressResponse> addresses = userService.getAddresses(userId);

        assertThat(addresses).hasSize(1);
        assertThat(addresses.get(0).city()).isEqualTo("Moscow");
    }

    @Test
    @DisplayName("Добавление нового адреса")
    void testAddAddressSuccessfulReturnsAddressResponse() {
        UUID userId = UUID.randomUUID();

        UserProfile profile = UserProfile.builder()
                .id(userId)
                .build();

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));

        when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(invocation -> {
            UserAddress saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        AddressRequest request = new AddressRequest(
                "Russia",
                "SPb",
                "Nevsky",
                "10",
                "333",
                null,
                true);
        AddressResponse response = userService.addAddress(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.city()).isEqualTo("SPb");
        assertThat(response.isDefault()).isTrue();
    }

    @Test
    @DisplayName("Удаление адреса: успешный сценарий")
    void testDeleteAddressSuccessful() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();

        UserProfile profile = UserProfile.builder()
                .id(userId)
                .build();

        UserAddress address = UserAddress.builder()
                .id(addressId)
                .userProfile(profile)
                .build();

        when(userAddressRepository.findByIdAndUserProfileId(addressId, userId)).thenReturn(Optional.of(address));
        userService.deleteAddress(addressId, userId);

        verify(userAddressRepository).delete(address);
    }

    @Test
    @DisplayName("Удаление чужого адреса (IDOR)")
    void testDeleteForeignAddressThrowsNoSuchElementException() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();

        when(userAddressRepository.findByIdAndUserProfileId(addressId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteAddress(addressId, userId))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Address not found with id=" +  addressId + " for user=" + userId);
        verify(userAddressRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Обновление ролей пользователя")
    void testUpdateRolesSuccessfulReturnsUserProfileResponse() {
        UUID userId = UUID.randomUUID();

        UserProfile profile = UserProfile.builder()
                .id(userId)
                .roles(Set.of("ROLE_USER"))
                .build();

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));

        UpdateRolesRequest request = new UpdateRolesRequest(Set.of("ROLE_USER", "ROLE_ADMIN"));
        UserProfileResponse response = userService.updateRoles(userId, request);

        assertThat(response.roles()).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

}
