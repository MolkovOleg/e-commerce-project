package com.ecommerce.apigateway.filter;

import com.ecommerce.apigateway.security.JwtValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLES_HEADER = "X-User-Roles";

    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/api/v1/auth",
            "/actuator",
            "/v3/api-docs",
            "/swagger-ui"
    );

    private final JwtValidator jwtValidator;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return onError(exchange);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        if (!jwtValidator.isTokenValid(token)) {
            log.warn("Invalid or expired JWT token for path: {}", path);
            return onError(exchange);
        }

        String userId = jwtValidator.extractUserId(token);
        List<String> roles = jwtValidator.extractRoles(token);
        String rolesHeader = roles != null ? String.join(", ", roles) : "";

        ServerHttpRequest mutatedRequest = request.mutate()
                .header(USER_ID_HEADER, userId != null ? userId : "")
                .header(USER_ROLES_HEADER, rolesHeader)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private Mono<Void> onError(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
