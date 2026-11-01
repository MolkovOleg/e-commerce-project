package com.ecommerce.notificationservice.config;

import com.ecommerce.notificationservice.domain.NotificationChannel;
import com.ecommerce.notificationservice.domain.NotificationTemplate;
import com.ecommerce.notificationservice.domain.NotificationType;
import com.ecommerce.notificationservice.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationTemplateInitializer implements CommandLineRunner {

    private final NotificationTemplateRepository templateRepository;

    @Override
    public void run(String... args) throws Exception {
        loadTemplateIfMissing(
                NotificationType.ORDER_CREATED,
                NotificationChannel.EMAIL,
                "Order #{{orderId}} has been created!",
                "Hello! Your order #{{orderId}} for the total amount of {{totalAmount}} " +
                        "has been successfully placed and reserved (status: {{status}})."
        );

        loadTemplateIfMissing(
                NotificationType.ORDER_STATUS_CHANGED,
                NotificationChannel.EMAIL,
                "Order #{{orderId}} status updated to {{newStatus}}",
                "Hello! The status of your order #{{orderId}} has changed from {{oldStatus}} to {{newStatus}}. " +
                        "Reason {{reason}}."
        );

        loadTemplateIfMissing(
                NotificationType.ORDER_CREATED,
                NotificationChannel.SMS,
                null,
                "E-Commerce: Order #{{orderId}} reserved. Total: {{totalAmount}}."
        );

        loadTemplateIfMissing(
                NotificationType.ORDER_STATUS_CHANGED,
                NotificationChannel.SMS,
                null,
                "E-Commerce: Order #{{orderId}} status changed to {{newStatus}}."
        );
    }

    private void loadTemplateIfMissing(
            NotificationType type,
            NotificationChannel channel,
            String subjectTemplate,
            String bodyTemplate) {
        if (!templateRepository.existsByTypeAndChannel(type, channel)) {
            NotificationTemplate template = NotificationTemplate.builder()
                    .type(type)
                    .channel(channel)
                    .subjectTemplate(subjectTemplate)
                    .bodyTemplate(bodyTemplate)
                    .build();

            templateRepository.save(template);
            log.info("Default notification template has been loaded: type={}, channel={}", type, channel);
        }
    }
}
