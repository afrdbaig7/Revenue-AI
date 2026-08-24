package com.recoverai.common.util;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Primary rate limiter: Redis when reachable, in-memory fallback otherwise. Call sites
 * inject {@link RateLimiter} and stay backend-agnostic.
 */
@Configuration
public class RateLimiterConfig {

  @Bean
  @Primary
  public RateLimiter rateLimiter(RedisRateLimiter redis, InMemoryRateLimiter memory) {
    return (key, max, window) -> redis.tryAcquire(key, max, window);
  }
}
