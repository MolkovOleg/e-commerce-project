package com.ecommerce.notificationservice.repository;

import com.ecommerce.notificationservice.domain.NotificationChannel;
import com.ecommerce.notificationservice.domain.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationLogRepository extends MongoRepository<NotificationLog, String> {

    boolean existsByEventIdAndChannel(String eventId,  NotificationChannel channel);

    Page<NotificationLog> findAllByUserId(String userId, Pageable pageable);
}
