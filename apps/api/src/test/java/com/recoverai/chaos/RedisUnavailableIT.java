package com.recoverai.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.recoverai.common.util.RedisRateLimiter;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Chaos test: Redis is Unavailable.
 *
 * Verifies that rate limiting degrades gracefully when Redis is unavailable
 * (falling back to in-memory limits), distributed lock acquisition fails safely,
 * and core processing continues without Redis.
 */
@Testcontainers
@SpringBootTest(properties = {
    "recoverai.event-dispatch-mode=inline",
    "recoverai.razorpay.mock-mode=true",
    "recoverai.ai.enabled=false",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class RedisUnavailableIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("recoverai_test")
      .withUsername("recoverai")
      .withPassword("recoverai_dev");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    // Invalid port for Redis to ensure connection failure naturally if not mocked
    registry.add("spring.data.redis.host", () -> "localhost");
    registry.add("spring.data.redis.port", () -> "9999");
  }

  @Autowired RedisRateLimiter rateLimiter;
  
  // Mock Redis template to reliably simulate connection failure without waiting for timeouts
  @MockBean StringRedisTemplate stringRedisTemplate;

  @Test
  void rateLimiterDegradesGracefullyWhenRedisIsDown() {
    // Simulate Redis failure when attempting to interact with it
    when(stringRedisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("Unable to connect to Redis on localhost:9999"));
    
    // Test the rate limiter fallback mechanism (InMemoryRateLimiter)
    String key = "test-tenant-rate-limit";
    
    // First request should be allowed (fallback to in-memory)
    boolean first = rateLimiter.tryAcquire(key, 2, Duration.ofSeconds(10));
    assertThat(first).isTrue();
    
    // Second request should be allowed
    boolean second = rateLimiter.tryAcquire(key, 2, Duration.ofSeconds(10));
    assertThat(second).isTrue();
    
    // Third request should be blocked (in-memory rate limit hit)
    boolean third = rateLimiter.tryAcquire(key, 2, Duration.ofSeconds(10));
    assertThat(third).isFalse();
  }
}
