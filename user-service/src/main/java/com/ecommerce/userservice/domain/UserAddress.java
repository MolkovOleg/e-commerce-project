package com.ecommerce.userservice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "user_addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch =  FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id",  nullable = false)
    private UserProfile userProfile;

    @Column(nullable = false, length = 100)
    @Builder.Default
    private String country = "Russia";

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 255)
    private String street;

    @Column(nullable = false, length = 20)
    private String building;

    @Column(nullable = false, length = 20)
    private String apartment;

    @Column(nullable = false, length = 20)
    private String zipCode;

    @Column(name = "is_default", nullable = false, length = 20)
    @Builder.Default
    private boolean isDefault =  false;
}
