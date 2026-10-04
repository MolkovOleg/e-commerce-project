package com.ecommerce.userservice.repository;

import com.ecommerce.userservice.domain.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, UUID> {
    List<UserAddress> findAllByUserProfileId(UUID addressId);
    Optional<UserAddress> findByIdAndUserProfileId(UUID addressId, UUID profileId);
}
