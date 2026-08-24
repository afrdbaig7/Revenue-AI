package com.recoverai.common.util;

import java.time.Duration;

/** Rate-limit backend abstraction (sliding/fixed window). */
public interface RateLimiter {

  /** @return true when the request is allowed (within {@code max} per {@code window}). */
  boolean tryAcquire(String key, int max, Duration window);
}
