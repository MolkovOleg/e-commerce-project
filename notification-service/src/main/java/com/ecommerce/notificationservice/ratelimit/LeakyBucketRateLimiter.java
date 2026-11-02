package com.ecommerce.notificationservice.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class LeakyBucketRateLimiter {

    private static final String REDIS_KEY_PREFIX = "rate_limit:notif:";
    private static final long KEY_TTL_SECONDS = 3600; // 1 час автоочистки неактивных корзин

    private static final String LEAKY_BUCKET_LUA_SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local leak_rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local ttl = tonumber(ARGV[4])
            local data = redis.call('HMGET', key, 'water', 'last_leak')
            local water = tonumber(data[1])
            local last_leak = tonumber(data[2])
            if not water or not last_leak then
                water = 0
                last_leak = now
            else
                local elapsed = math.max(0, now - last_leak)
                water = math.max(0, water - (elapsed * leak_rate))
                last_leak = now
            end
            if water + 1 <= capacity then
                water = water + 1
                redis.call('HSET', key, 'water', tostring(water), 'last_leak', tostring(last_leak))
                redis.call('EXPIRE', key, ttl)
                return 1
            else
                redis.call('HSET', key, 'water', tostring(water), 'last_leak', tostring(last_leak))
                redis.call('EXPIRE', key, ttl)
                return 0
            end
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> redisScript;
    private final double capacity;
    private final double leakRatePerSecond;

    public LeakyBucketRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${notification.rate-limiter.capacity:5}") double capacity,
            @Value("${notification.rate-limiter.leak-rate-per-second:0.1}") double leakRatePerSecond) {
        this.redisTemplate = redisTemplate;
        this.capacity = capacity;
        this.leakRatePerSecond = leakRatePerSecond;

        this.redisScript = new DefaultRedisScript<>();
        this.redisScript.setScriptText(LEAKY_BUCKET_LUA_SCRIPT);
        this.redisScript.setResultType(Long.class);
    }

    /**
     * Проверяет, можно ли отправить уведомление пользователю
     */
    public boolean tryAcquire(String userId) {
        String key = REDIS_KEY_PREFIX + userId;
        List<String> keys = Collections.singletonList(key);

        double nowInSeconds = System.currentTimeMillis() / 1000.0;

        try {
            Long result = redisTemplate.execute(
                    redisScript,
                    keys,
                    String.valueOf(capacity),
                    String.valueOf(leakRatePerSecond),
                    String.valueOf(nowInSeconds),
                    String.valueOf(KEY_TTL_SECONDS)
            );

            boolean allowed = result != null && result == 1L;

            if (!allowed) {
                log.warn("Rate limit exceeded for userId={}", userId);
            }
            return allowed;
        } catch (Exception ex) {
            // Если Redis временно упал, не блокируем доставку критических уведомлений
            log.error("Error executing Redis rate limitter for userId={}. Allowing notification as fallback",
                    userId, ex);
            return true;
        }
    }

}
