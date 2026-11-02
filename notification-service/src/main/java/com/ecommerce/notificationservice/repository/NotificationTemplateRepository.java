package com.ecommerce.notificationservice.repository;

import com.ecommerce.notificationservice.domain.NotificationChannel;
import com.ecommerce.notificationservice.domain.NotificationTemplate;
import com.ecommerce.notificationservice.domain.NotificationType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationTemplateRepository extends MongoRepository<NotificationTemplate, String> {

    Optional<NotificationTemplate> findByTypeAndChannelAndActiveTrue(
            NotificationType type,
            NotificationChannel channel
    );

    List<NotificationTemplate> findAllByTypeAndActiveTrue(NotificationType type);

    boolean existsByTypeAndChannel(NotificationType type, NotificationChannel channel);
}
