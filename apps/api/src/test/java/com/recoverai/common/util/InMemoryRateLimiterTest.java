package com.recoverai.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTest {

  private final InMemoryRateLimiter limiter = new InMemoryRateLimiter();

  @Test
  void blocksRequestsAfterTheLimitIsReached() {
    assertThat(limiter.tryAcquire("tenant", 2, Duration.ofMinutes(1))).isTrue();
    assertThat(limiter.tryAcquire("tenant", 2, Duration.ofMinutes(1))).isTrue();
    assertThat(limiter.tryAcquire("tenant", 2, Duration.ofMinutes(1))).isFalse();
    assertThat(limiter.tryAcquire("tenant", 2, Duration.ofMinutes(1))).isFalse();
  }

  @Test
  void tracksKeysIndependently() {
    assertThat(limiter.tryAcquire("tenant-a", 1, Duration.ofMinutes(1))).isTrue();
    assertThat(limiter.tryAcquire("tenant-a", 1, Duration.ofMinutes(1))).isFalse();
    assertThat(limiter.tryAcquire("tenant-b", 1, Duration.ofMinutes(1))).isTrue();
  }

  @Test
  void rejectsRequestsWhenTheLimitIsZero() {
    assertThat(limiter.tryAcquire("tenant", 0, Duration.ofMinutes(1))).isFalse();
  }

  @Test
  void clearResetsAllLimits() {
    assertThat(limiter.tryAcquire("tenant", 1, Duration.ofMinutes(1))).isTrue();
    assertThat(limiter.tryAcquire("tenant", 1, Duration.ofMinutes(1))).isFalse();

    limiter.clear();

    assertThat(limiter.tryAcquire("tenant", 1, Duration.ofMinutes(1))).isTrue();
  }
}
