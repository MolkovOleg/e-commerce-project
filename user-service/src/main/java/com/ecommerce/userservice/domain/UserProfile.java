package com.ecommerce.userservice.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "first_name",  length = 100)
    private String firstName;

    @Column(name = "last_name",   length = 100)
    private String lastName;

    @Column(length = 100)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private UserProfileStatus status =  UserProfileStatus.ACTIVE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_profile_roles",
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "role", nullable = false, length = 50)
    @Builder.Default
    private Set<String> roles = new HashSet<>();

    @OneToMany(
            mappedBy = "userProfile",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<UserAddress> addresses = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at",  nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Методы для поддержания консистентности
    public void addAddress(UserAddress address) {
        this.addresses.add(address);
        address.setUserProfile(this);
    }

    public void removeAddress(UserAddress address) {
        this.addresses.remove(address);
        address.setUserProfile(null);
    }
}
