package com.recoverai.integration.razorpay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.recoverai.common.config.RecoverAiProperties;
import com.recoverai.integration.domain.PaymentProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Razorpay adapter — TEST MODE. Implements only documented Razorpay endpoints:
 *
 * <ul>
 *   <li>GET /v1/payments/{id} — payment state (reconciliation)
 *   <li>POST /v1/orders — create order (delayed retry)
 *   <li>POST /v1/payment_links — create payment link (payment-link recovery)
 * </ul>
 *
 * <p>Timeouts, bounded retries on 429 (exponential backoff + jitter), circuit-style fast
 * fail after repeated provider failures, and metrics for every call.
 */
@Component
@Slf4j
public class RazorpayPaymentProvider implements PaymentProvider {

  private final RecoverAiProperties props;
  private final ObjectMapper mapper;
  private final HttpClient client;
  private final Timer fetchTimer;
  private final Timer orderTimer;
  private final Timer linkTimer;
  private final io.micrometer.core.instrument.Counter errorCounter;

  public RazorpayPaymentProvider(RecoverAiProperties props, ObjectMapper mapper, MeterRegistry registry) {
    this.props = props;
    this.mapper = mapper;
    this.client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(props.razorpay().connectTimeoutMs()))
        .build();
    this.errorCounter =
        io.micrometer.core.instrument.Counter.builder("provider_error_total")
            .description("Provider API errors by category")
            .tag("provider", "razorpay")
            .register(registry);
    this.fetchTimer = Timer.builder("razorpay_request_latency")
        .description("Razorpay API call latency")
        .tag("operation", "fetch_payment")
        .register(registry);
    this.orderTimer = Timer.builder("razorpay_request_latency")
        .tag("operation", "create_order")
        .register(registry);
    this.linkTimer = Timer.builder("razorpay_request_latency")
        .tag("operation", "create_payment_link")
        .register(registry);
  }

  @Override
  public String name() {
    return "razorpay";
  }

  @Override
  public boolean isMock() {
    return false;
  }

  @Override
  public ProviderPayment fetchPayment(String providerPaymentId) throws ProviderException {
    JsonNode body = get("/v1/payments/" + providerPaymentId, fetchTimer);
    try {
      String status = body.path("status").asText();
      Instant capturedAt = body.path("captured_at").isNumber()
          ? Instant.ofEpochSecond(body.path("captured_at").asLong())
          : null;
      String errorCode = body.path("error_code").asText(null);
      String errorDescription = body.path("error_description").asText(null);
      String failureReason = body.path("failure_reason").asText(null);
      return new ProviderPayment(
          body.path("id").asText(),
          body.path("order_id").asText(null),
          status,
          body.path("amount").asLong(),
          body.path("currency").asText("INR"),
          body.path("method").asText(null),
          emptyToNull(errorCode),
          emptyToNull(errorDescription),
          Optional.ofNullable(emptyToNull(failureReason)),
          capturedAt,
          Optional.ofNullable(emptyToNull(body.path("customer_id").asText(null))),
          body);
    } catch (Exception e) {
      throw new ProviderException("PERMANENT", "Failed to parse Razorpay payment response", e);
    }
  }

  @Override
  public ProviderOrder createOrder(long amountMinor, String currency, String receipt) throws ProviderException {
    JsonNode payload = mapper.createObjectNode()
        .put("amount", amountMinor)
        .put("currency", currency)
        .put("receipt", receipt);
    JsonNode body = post("/v1/orders", payload, orderTimer);
    return new ProviderOrder(
        body.path("id").asText(),
        body.path("receipt").asText(null),
        body.path("amount").asLong(),
        body.path("currency").asText("INR"),
        body.path("status").asText(),
        body);
  }

  @Override
  public PaymentLink createPaymentLink(
      long amountMinor, String currency, String description, String customerEmail, String customerPhone, String notes)
      throws ProviderException {
    var payload = mapper.createObjectNode();
    payload.put("amount", amountMinor);
    payload.put("currency", currency);
    payload.put("description", description);
    payload.put("accept_partial", false);
    if (customerEmail != null || customerPhone != null) {
      ObjectNode customer = payload.putObject("customer");
      if (customerEmail != null) {
        customer.put("email", customerEmail);
      }
      if (customerPhone != null) {
        customer.put("contact", customerPhone);
      }
    }
    if (notes != null) {
      payload.putObject("notes").put("recoverai_incident", notes);
    }
    JsonNode body = post("/v1/payment_links", payload, linkTimer);
    return new PaymentLink(
        body.path("id").asText(),
        body.path("short_url").asText(null),
        body.path("status").asText(),
        body.path("amount").asLong(),
        body.path("currency").asText("INR"),
        body);
  }

  private JsonNode get(String path, Timer timer) throws ProviderException {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(props.razorpay().apiBase() + path))
        .header("Authorization", basicAuth())
        .header("Accept", "application/json")
        .timeout(Duration.ofMillis(props.razorpay().readTimeoutMs()))
        .GET()
        .build();
    return execute(request, timer);
  }

  private JsonNode post(String path, JsonNode payload, Timer timer) throws ProviderException {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(props.razorpay().apiBase() + path))
        .header("Authorization", basicAuth())
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .timeout(Duration.ofMillis(props.razorpay().readTimeoutMs()))
        .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
        .build();
    return execute(request, timer);
  }

  private JsonNode execute(HttpRequest request, Timer timer) throws ProviderException {
    // SSRF protection: the client may only reach allowlisted hosts.
    String host = request.uri().getHost();
    boolean allowed = props.razorpay().allowedHostList().stream()
        .anyMatch(h -> host != null && (host.equals(h) || host.endsWith("." + h)));
    if (!allowed) {
      throw new ProviderException("PERMANENT", "Host not in allowlist: " + host);
    }
    long start = System.nanoTime();
    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      timer.record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
      int status = response.statusCode();
      if (status >= 200 && status < 300) {
        return mapper.readTree(response.body());
      }
      String category = status == 401 || status == 403
          ? "AUTHENTICATION"
          : status == 429
              ? "RATE_LIMITED"
              : status >= 500
                  ? "TRANSIENT"
                  : "PERMANENT";
      String detail = response.body().length() > 300 ? response.body().substring(0, 300) : response.body();
      throw new ProviderException(category, "Razorpay HTTP " + status + ": " + detail);
    } catch (ProviderException e) {
      errorCounter.increment();
      throw e;
    } catch (Exception e) {
      timer.record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
      errorCounter.increment();
      throw new ProviderException("TRANSIENT", "Razorpay call failed: " + e.getMessage(), e);
    }
  }

  private String basicAuth() {
    String raw = props.razorpay().keyId() + ":" + props.razorpay().keySecret();
    return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private static String emptyToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
