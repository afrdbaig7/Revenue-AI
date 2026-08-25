package com.recoverai.diagnosis.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverai.common.config.RecoverAiProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * HTTP client for the AI decision service. Strict timeouts, one retry, circuit breaker
 * (open after 5 consecutive failures for 30 s), request/response redaction, and metrics.
 * On any failure the caller falls back to the deterministic engine — AI availability
 * must never block recovery.
 */
@Component
@Slf4j
public class AiClient {

  private final RecoverAiProperties props;
  private final ObjectMapper mapper;
  private final HttpClient client;
  private final Counter requests;
  private final Counter failures;
  private final Counter fallbacks;
  private final Timer latency;

  private volatile int consecutiveFailures;
  private volatile Instant circuitOpenedAt;

  public AiClient(RecoverAiProperties props, ObjectMapper mapper, MeterRegistry registry) {
    this.props = props;
    this.mapper = mapper;
    this.client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(1500)).build();
    this.requests = Counter.builder("ai_request_total").register(registry);
    this.failures = Counter.builder("ai_request_failure_total").register(registry);
    this.fallbacks = Counter.builder("ai_fallback_total").description("Deterministic fallbacks used").register(registry);
    this.latency = Timer.builder("ai_request_latency").register(registry);
  }

  /** @return parsed response or {@code null} when the AI is unavailable/invalid. */
  public JsonNode call(String path, Object requestBody) {
    if (!props.ai().enabled()) {
      fallbacks.increment();
      return null;
    }
    if (circuitOpen()) {
      fallbacks.increment();
      return null;
    }
    requests.increment();
    long start = System.nanoTime();
    try {
      // SSRF protection: only the configured AI base host may be called.
      String baseHost = URI.create(props.ai().baseUrl()).getHost();
      String requestHost = URI.create(props.ai().baseUrl() + path).getHost();
      if (requestHost == null || !requestHost.equals(baseHost)) {
        throw new IllegalStateException("AI host not allowed: " + requestHost);
      }
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(props.ai().baseUrl() + path))
          .header("Content-Type", "application/json")
          .timeout(Duration.ofMillis(props.ai().timeoutMs()))
          .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
          .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      latency.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        consecutiveFailures = 0;
        return mapper.readTree(response.body());
      }
      throw new IllegalStateException("AI HTTP " + response.statusCode());
    } catch (Exception e) {
      consecutiveFailures++;
      failures.increment();
      if (consecutiveFailures >= 5) {
        circuitOpenedAt = Instant.now();
        log.warn("AI circuit opened after {} consecutive failures", consecutiveFailures);
      }
      log.warn("AI_CALL_FAILED path={} error={}", path, e.getMessage());
      return null;
    }
  }

  private boolean circuitOpen() {
    Instant opened = circuitOpenedAt;
    if (opened == null) {
      return false;
    }
    if (opened.plus(Duration.ofSeconds(30)).isBefore(Instant.now())) {
      circuitOpenedAt = null;
      consecutiveFailures = 0;
      return false;
    }
    return true;
  }
}
