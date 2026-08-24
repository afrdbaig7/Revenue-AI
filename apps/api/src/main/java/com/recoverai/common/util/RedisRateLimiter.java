package com.recoverai.common.util;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed fixed-window rate limiter (INCR + EXPIRE) for tenant-aware quotas and
 * cross-instance enforcement. Degrades gracefully to the in-memory limiter when Redis
 * is unreachable — rate limiting must never become an availability risk.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisRateLimiter implements RateLimiter {

  private static final String PREFIX = "recoverai:rl:";

  private final StringRedisTemplate redis;
  private final InMemoryRateLimiter fallback;

  @Override
  public boolean tryAcquire(String key, int max, Duration window) {
    try {
      String k = PREFIX + key;
      Long count = redis.opsForValue().increment(k);
      if (count != null && count == 1L) {
        redis.expire(k, window);
      }
      return count != null && count <= max;
    } catch (Exception e) {
      log.debug("REDIS_RATE_LIMIT_FALLBACK key={} error={}", key, e.getMessage());
      return fallback.tryAcquire(key, max, window);
    }
  }
}
