package com.ecommerce.notificationservice.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LeakyBucketRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private LeakyBucketRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new LeakyBucketRateLimiter(redisTemplate, 5.0, 0.1);
    }

    @Test
    @DisplayName("Разрешает отправку (true), когда Lua-скрипт возвращает 1")
    void tryAcquireShouldReturnTrueWhenRedisReturnsOne() {

        when(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                anyList(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(1L);

        assertThat(rateLimiter.tryAcquire("user-1")).isTrue();
    }

    @Test
    @DisplayName("Блокирует отправку (false), когда корзина переполнена и Lua-скрипт возвращает 0")
    void tryAcquireShouldReturnFalseWhenRedisReturnsZero() {
        when(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                anyList(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(0L);

        assertThat(rateLimiter.tryAcquire("user-1")).isFalse();
    }

    @Test
    @DisplayName("Разрешает отправку (true) при недостпуности Redis")
    void tryAcquireShouldReturnTrueOnRedisException() {
        when(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                anyList(),
                any(),
                any(),
                any(),
                any()
        )).thenThrow(new RedisConnectionFailureException("Redis down"));

        assertThat(rateLimiter.tryAcquire("user-1")).isTrue();
    }
}
