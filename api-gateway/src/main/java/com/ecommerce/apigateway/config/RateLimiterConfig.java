package com.ecommerce.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RateLimiterConfig {

    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> {
            // Для авторизованных запросов
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                return Mono.just(userId);
            }

            // Для всех остальных запросов (без авторизации)
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            String ip = remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "anonymous";
            return Mono.just(ip);
        };
    }
}
