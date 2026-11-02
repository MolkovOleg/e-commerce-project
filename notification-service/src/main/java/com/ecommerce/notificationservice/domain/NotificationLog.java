package com.ecommerce.notificationservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "notification_logs")
@CompoundIndexes({
        @CompoundIndex(name = "user_created_idx", def = "{'userId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "event_channel_unique_idx", def = "{'eventId': 1, 'channel': -1}", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    private static final int THIRTY_DAYS_IN_SECONDS = 30 * 24 * 60 * 60;

    @Id
    private String id;

    private String eventId;

    private String userId;

    private String orderId;

    private NotificationType type;

    private NotificationChannel channel;

    private String subject;

    private String body;

    private NotificationStatus status;

    private String errorMessage;

    @Indexed(name = "created_at_ttl_idx", expireAfterSeconds = THIRTY_DAYS_IN_SECONDS)
    @Builder.Default
    private Instant createdAt =  Instant.now();
}
