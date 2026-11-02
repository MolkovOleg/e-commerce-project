package com.ecommerce.notificationservice.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaConfig {

    private static final long RETRY_INTERVAL = 1000L;
    private static final long MAX_RETRY_ATTEMPTS = 3L;
    private static final String DLT_SUFFIX = ".DLT";

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaOperations<?, ?> kafkaOperations) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (record, ex) -> {
                    String dltTopic = record.topic() + DLT_SUFFIX;
                    log.error("Retries exhaused for topic={}, partition={}, offset={}. Sending to DLT={}",
                            record.topic(), record.partition(), record.offset(), dltTopic, ex);
                    return new TopicPartition(dltTopic, record.partition());
                }
        );

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(RETRY_INTERVAL, MAX_RETRY_ATTEMPTS)
        );

        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class, IllegalStateException.class);

        return errorHandler;
    }
}
