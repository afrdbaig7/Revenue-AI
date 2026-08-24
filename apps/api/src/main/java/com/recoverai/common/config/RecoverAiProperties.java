package com.recoverai.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Central typed configuration for RecoverAI runtime knobs. */
@ConfigurationProperties(prefix = "recoverai")
public record RecoverAiProperties(
    boolean demoMode,
    String eventDispatchMode,
    Temporal temporal,
    Razorpay razorpay,
    Ai ai,
    Jwt jwt,
    Encryption encryption,
    Scheduling scheduling) {

  public boolean kafkaDispatch() {
    return "kafka".equalsIgnoreCase(eventDispatchMode);
  }

  public record Temporal(boolean enabled, String target, String namespace) {}

  public record Razorpay(
      String keyId,
      String keySecret,
      String webhookSecret,
      String apiBase,
      boolean mockMode,
      int connectTimeoutMs,
      int readTimeoutMs,
      String allowedHosts) {

    /** Hosts the provider HTTP client may call (SSRF allowlist), comma-separated. */
    public java.util.List<String> allowedHostList() {
      if (allowedHosts == null || allowedHosts.isBlank()) {
        return java.util.List.of("api.razorpay.com");
      }
      return java.util.Arrays.stream(allowedHosts.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
  }

  public record Ai(String baseUrl, int timeoutMs, boolean enabled) {}

  public record Jwt(
      String issuer, long accessTtlMinutes, long refreshTtlDays, boolean secureCookies) {
    public Duration accessTtl() {
      return Duration.ofMinutes(accessTtlMinutes);
    }

    public Duration refreshTtl() {
      return Duration.ofDays(refreshTtlDays);
    }
  }

  public record Encryption(String key) {}

  public record Scheduling(long outboxPollMs, long actionPollMs, long reconcilePollMs, String snapshotCron) {}
}
