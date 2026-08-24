package com.recoverai.common.util;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Lightweight in-memory sliding-window rate limiter. Suitable per instance; used as the
 * fallback when Redis is unavailable (bounded map, best-effort).
 */
@Component
public class InMemoryRateLimiter implements RateLimiter {

  private record Window(long windowStart, int count) {}

  private final Map<String, Window> buckets = new ConcurrentHashMap<>();

  @Override
  public boolean tryAcquire(String key, int max, Duration window) {
    long now = System.currentTimeMillis();
    long windowMillis = window.toMillis();
    return buckets.compute(
            key,
            (k, w) -> {
              if (w == null || now - w.windowStart >= windowMillis) {
                return new Window(now, 1);
              }
              if (w.count >= max) {
                return w;
              }
              return new Window(w.windowStart, w.count + 1);
            })
        .count() <= max;
  }

  public void clear() {
    buckets.clear();
  }
}
