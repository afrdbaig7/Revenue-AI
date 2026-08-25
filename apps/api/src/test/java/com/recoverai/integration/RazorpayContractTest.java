package com.recoverai.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverai.common.config.RecoverAiProperties;
import com.recoverai.integration.domain.PaymentProvider;
import com.recoverai.integration.domain.PaymentProvider.PaymentLink;
import com.recoverai.integration.domain.PaymentProvider.ProviderOrder;
import com.recoverai.integration.domain.PaymentProvider.ProviderPayment;
import com.recoverai.integration.razorpay.RazorpayPaymentProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Contract test for the Razorpay adapter: verifies the exact HTTP contract the adapter
 * produces (path, Basic auth header, JSON bodies) and how documented responses/errors
 * map into {@link PaymentProvider} types — against a local stub, no real credentials.
 */
class RazorpayContractTest {

  private static final String KEY_ID = "rzp_test_contract";
  private static final String KEY_SECRET = "contract_secret";
  private static HttpServer server;
  private static String baseUrl;
  private static final AtomicReference<String> lastPath = new AtomicReference<>();
  private static final AtomicReference<String> lastAuth = new AtomicReference<>();
  private static final AtomicReference<String> lastBody = new AtomicReference<>();
  private static final AtomicReference<Integer> nextStatus = new AtomicReference<>(200);

  @BeforeAll
  static void startStub() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          byte[] body = exchange.getRequestBody().readAllBytes();
          lastPath.set(exchange.getRequestURI().getPath());
          lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
          lastBody.set(new String(body, StandardCharsets.UTF_8));
          int status = nextStatus.get();
          String response = switch (status) {
            case 200 -> switch (exchange.getRequestURI().getPath()) {
              case "/v1/payments/pay_1" ->
                  "{\"id\":\"pay_1\",\"status\":\"failed\",\"amount\":349900,\"currency\":\"INR\","
                      + "\"method\":\"card\",\"error_code\":\"INSUFFICIENT_FUNDS\","
                      + "\"error_description\":\"The bank reported insufficient funds\",\"failure_reason\":\"bank_declined\"}";
              case "/v1/orders" ->
                  "{\"id\":\"order_1\",\"receipt\":\"rec-1\",\"amount\":349900,\"currency\":\"INR\",\"status\":\"created\"}";
              case "/v1/payment_links" ->
                  "{\"id\":\"plink_1\",\"short_url\":\"https://rzp.io/i/abc\",\"amount\":349900,\"currency\":\"INR\",\"status\":\"created\"}";
              default -> "{\"error\":{\"code\":\"BAD_REQUEST_ERROR\",\"description\":\"unknown\"}}";
            };
            case 429 -> "{\"error\":{\"code\":\"RATE_LIMITED\",\"description\":\"Too many requests\"}}";
            case 401 -> "{\"error\":{\"code\":\"BAD_REQUEST_ERROR\",\"description\":\"Invalid API key\"}}";
            case 500 -> "{\"error\":{\"code\":\"SERVER_ERROR\",\"description\":\"boom\"}}";
            default -> "{}";
          };
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, response.getBytes(StandardCharsets.UTF_8).length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes(StandardCharsets.UTF_8));
          }
        });
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterAll
  static void stopStub() {
    server.stop(0);
  }

  private RazorpayPaymentProvider provider() {
    RecoverAiProperties props = new RecoverAiProperties(
        true,
        "inline",
        new RecoverAiProperties.Temporal(false, "localhost:7233", "recoverai"),
        new RecoverAiProperties.Razorpay(
            KEY_ID, KEY_SECRET, "whsec", baseUrl, false, 2000, 4000, "127.0.0.1,localhost,api.razorpay.com"),
        new RecoverAiProperties.Ai("http://localhost:8100", 8000, true),
        new RecoverAiProperties.Jwt("test", 15, 7, false),
        new RecoverAiProperties.Encryption("test-key"),
        new RecoverAiProperties.Scheduling(1000, 5000, 30000, "0 0 * * * *"));
    return new RazorpayPaymentProvider(props, new ObjectMapper(), new SimpleMeterRegistry());
  }

  private static String basicAuth() {
    String raw = KEY_ID + ":" + KEY_SECRET;
    return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void fetchPaymentHitsDocumentedEndpointWithBasicAuth() throws Exception {
    nextStatus.set(200);
    ProviderPayment payment = provider().fetchPayment("pay_1");
    assertThat(lastPath.get()).isEqualTo("/v1/payments/pay_1");
    assertThat(lastAuth.get()).isEqualTo(basicAuth());
    assertThat(payment.status()).isEqualTo("failed");
    assertThat(payment.amountMinor()).isEqualTo(349900);
    assertThat(payment.errorCode()).isEqualTo("INSUFFICIENT_FUNDS");
    assertThat(payment.currency()).isEqualTo("INR");
  }

  @Test
  void createOrderPostsAmountCurrencyReceipt() throws Exception {
    nextStatus.set(200);
    ProviderOrder order = provider().createOrder(349900, "INR", "rec-1");
    assertThat(lastPath.get()).isEqualTo("/v1/orders");
    assertThat(lastBody.get()).contains("\"amount\":349900").contains("\"currency\":\"INR\"").contains("\"receipt\":\"rec-1\"");
    assertThat(order.id()).isEqualTo("order_1");
  }

  @Test
  void createPaymentLinkSendsCustomerAndNotes() throws Exception {
    nextStatus.set(200);
    PaymentLink link = provider().createPaymentLink(349900, "INR", "desc", "cust@example.com", "+919000000000", "incident-1");
    assertThat(lastPath.get()).isEqualTo("/v1/payment_links");
    assertThat(lastBody.get()).contains("\"email\":\"cust@example.com\"").contains("\"contact\":\"+919000000000\"")
        .contains("incident-1").contains("\"accept_partial\":false");
    assertThat(link.shortUrl()).isEqualTo("https://rzp.io/i/abc");
  }

  @Test
  void maps429ToRateLimited() {
    nextStatus.set(429);
    assertThatThrownBy(() -> provider().fetchPayment("pay_1"))
        .isInstanceOf(PaymentProvider.ProviderException.class)
        .extracting(e -> ((PaymentProvider.ProviderException) e).category())
        .isEqualTo("RATE_LIMITED");
  }

  @Test
  void maps401ToAuthentication() {
    nextStatus.set(401);
    assertThatThrownBy(() -> provider().fetchPayment("pay_1"))
        .isInstanceOf(PaymentProvider.ProviderException.class)
        .extracting(e -> ((PaymentProvider.ProviderException) e).category())
        .isEqualTo("AUTHENTICATION");
  }

  @Test
  void maps5xxToTransient() {
    nextStatus.set(500);
    assertThatThrownBy(() -> provider().fetchPayment("pay_1"))
        .isInstanceOf(PaymentProvider.ProviderException.class)
        .extracting(e -> ((PaymentProvider.ProviderException) e).category())
        .isEqualTo("TRANSIENT");
  }

  @Test
  void rejectsHostsOutsideAllowlist() {
    RecoverAiProperties props = new RecoverAiProperties(
        true, "inline",
        new RecoverAiProperties.Temporal(false, "localhost:7233", "recoverai"),
        new RecoverAiProperties.Razorpay(KEY_ID, KEY_SECRET, "whsec", "https://evil.example.com", false, 2000, 4000, "api.razorpay.com"),
        new RecoverAiProperties.Ai("http://localhost:8100", 8000, true),
        new RecoverAiProperties.Jwt("test", 15, 7, false),
        new RecoverAiProperties.Encryption("test-key"),
        new RecoverAiProperties.Scheduling(1000, 5000, 30000, "0 0 * * * *"));
    RazorpayPaymentProvider provider = new RazorpayPaymentProvider(props, new ObjectMapper(), new SimpleMeterRegistry());
    assertThatThrownBy(() -> provider.fetchPayment("pay_1"))
        .isInstanceOf(PaymentProvider.ProviderException.class)
        .extracting(e -> ((PaymentProvider.ProviderException) e).category())
        .isEqualTo("PERMANENT");
  }
}
