package com.ecommerce.notificationservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "notification_templates")
@CompoundIndex(name = "type_channel_unique_idx", def = "{'type': 1, 'channel': 1}", unique = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationTemplate {

    @Id
    private String id;

    private NotificationType type;

    private NotificationChannel channel;

    private String subjectTemplate;

    private String bodyTemplate;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private Instant updatedAt = Instant.now();
}
