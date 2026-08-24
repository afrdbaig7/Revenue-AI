package com.recoverai.webhook.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverai.common.api.ApiError;
import com.recoverai.common.api.ErrorCode;
import com.recoverai.webhook.application.RazorpaySignatureVerifier;
import com.recoverai.webhook.application.RazorpaySignatureVerifier.VerificationResult;
import com.recoverai.webhook.application.WebhookIngestionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Razorpay webhook endpoint — mission-critical path. Order of operations:
 *
 * <ol>
 *   <li>read the RAW body (before any parsing)
 *   <li>verify the signature; invalid → 401, payload never parsed or trusted
 *   <li>persist to webhook_inbox (idempotent on provider_event_id)
 *   <li>enqueue via transactional outbox
 *   <li>respond 200 fast — no AI, no provider calls, no slow work
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

  private final RazorpaySignatureVerifier verifier;
  private final WebhookIngestionService ingestion;
  private final ObjectMapper mapper;
  private final com.recoverai.common.util.RateLimiter rateLimiter;

  private final Counter received;
  private final Counter duplicates;
  private final Counter invalidSignature;
  private final Timer processingTimer;

  public WebhookController(
      RazorpaySignatureVerifier verifier,
      WebhookIngestionService ingestion,
      ObjectMapper mapper,
      com.recoverai.common.util.RateLimiter rateLimiter,
      MeterRegistry registry) {
    this.verifier = verifier;
    this.ingestion = ingestion;
    this.mapper = mapper;
    this.rateLimiter = rateLimiter;
    this.received = Counter.builder("webhook_received_total").description("Webhooks received").register(registry);
    this.duplicates = Counter.builder("webhook_duplicate_total").description("Duplicate webhooks absorbed").register(registry);
    this.invalidSignature =
        Counter.builder("webhook_invalid_signature_total").description("Webhooks rejected by signature").register(registry);
    this.processingTimer =
        Timer.builder("webhook_processing_latency").description("Webhook ingest latency").register(registry);
  }

  @PostMapping(value = "/razorpay", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> razorpay(HttpServletRequest request) throws IOException {
    received.increment();
    long start = System.nanoTime();

    // Per-IP burst protection (Redis-backed, in-memory fallback). 600/min absorbs
    // legit Razorpay bursts; everything beyond is abusive or misconfigured.
    if (!rateLimiter.tryAcquire("webhook:ip:" + request.getRemoteAddr(), 600, java.time.Duration.ofMinutes(1))) {
      return ResponseEntity.status(429)
          .body(new ApiError(
              ErrorCode.RATE_LIMITED.name(), "Webhook rate limit exceeded", MDC.get("correlationId"), null));
    }

    // 1. Raw body before parsing.
    byte[] rawBody = request.getInputStream().readAllBytes();
    String signature = request.getHeader(RazorpaySignatureVerifier.SIGNATURE_HEADER);

    // 2. Verify before trusting anything.
    VerificationResult verification = verifier.verify(rawBody, signature);
    if (!verification.valid()) {
      invalidSignature.increment();
      return ResponseEntity.status(401)
          .body(new ApiError(
              ErrorCode.UNAUTHENTICATED.name(), "Invalid webhook signature: " + verification.reason(), MDC.get("correlationId"), null));
    }

    // 3. Minimal parse: event id + type only.
    JsonNode event;
    try {
      event = mapper.readTree(rawBody);
    } catch (Exception e) {
      return ResponseEntity.badRequest()
          .body(new ApiError(ErrorCode.VALIDATION_ERROR.name(), "Malformed webhook body", MDC.get("correlationId"), null));
    }
    String eventId = event.path("id").asText();
    String eventType = event.path("event").asText();
    if (eventId.isBlank() || eventType.isBlank()) {
      return ResponseEntity.badRequest()
          .body(new ApiError(ErrorCode.VALIDATION_ERROR.name(), "Webhook missing id/event", MDC.get("correlationId"), null));
    }

    // 4. Idempotent ingest + outbox enqueue (same DB transaction).
    WebhookIngestionService.IngestResult result =
        ingestion.ingest(verification.integration(), eventId, eventType, rawBody, event);
    if (result.duplicate()) {
      duplicates.increment();
    }

    processingTimer.record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
    return ResponseEntity.ok(Map.of("received", true, "duplicate", result.duplicate()));
  }
}
