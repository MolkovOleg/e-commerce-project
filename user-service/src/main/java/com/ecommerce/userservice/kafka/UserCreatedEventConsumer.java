package com.ecommerce.userservice.kafka;

import com.ecommerce.common.event.UserCreatedEvent;
import com.ecommerce.userservice.domain.ProcessedEvent;
import com.ecommerce.userservice.domain.UserProfile;
import com.ecommerce.userservice.domain.UserProfileStatus;
import com.ecommerce.userservice.repository.ProcessedEventRepository;
import com.ecommerce.userservice.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserCreatedEventConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final UserProfileRepository userProfileRepository;

    @Transactional
    @KafkaListener(topics = "user.created", groupId = "user-service-group")
    public void handleUserCreatedEvent(UserCreatedEvent event) {
        log.info("Received UserCreatedEvent with eventId={} for userId={}", event.getEventId(), event.getUserId());

        // Проверка идемпотентности
        if (processedEventRepository.existsById(event.getEventId())) {
            log.warn("Event with eventId={} already processed. Skipping duplicate", event.getEventId());
            return;
        }

        UserProfile userProfile = UserProfile.builder()
                .id(UUID.fromString(event.getUserId()))
                .email(event.getEmail())
                .status(UserProfileStatus.ACTIVE)
                .roles(new HashSet<>(event.getRoles()))
                .build();

        userProfileRepository.save(userProfile);

        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .build();

        processedEventRepository.save(processedEvent);

        log.info("Successfully created UserProfile for userId={}", event.getUserId());
    }
}
