package com.recoverai.common.util;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
    if (max <= 0) {
      return false;
    }
    long now = System.currentTimeMillis();
    long windowMillis = window.toMillis();
    AtomicBoolean allowed = new AtomicBoolean();
    buckets.compute(
        key,
        (k, w) -> {
          if (w == null || now - w.windowStart >= windowMillis) {
            allowed.set(true);
            return new Window(now, 1);
          }
          if (w.count >= max) {
            return w;
          }
          allowed.set(true);
          return new Window(w.windowStart, w.count + 1);
        });
    return allowed.get();
  }

  public void clear() {
    buckets.clear();
  }
}
