package com.recoverai.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.recoverai.merchant.application.SecretCipher;
import com.recoverai.merchant.domain.MerchantIntegration;
import com.recoverai.merchant.infrastructure.MerchantIntegrationRepository;
import com.recoverai.webhook.application.RazorpaySignatureVerifier;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Webhook signature verification — raw-body HMAC, verified before parsing. */
class SignatureVerifierTest {

  private static final String SECRET = "recoverai_demo_webhook_secret";
  private static final String BODY = "{\"id\":\"evt_1\",\"event\":\"payment.failed\",\"payload\":{}}";

  private MerchantIntegrationRepository integrations;
  private SecretCipher cipher;
  private RazorpaySignatureVerifier verifier;

  @BeforeEach
  void setUp() {
    integrations = mock(MerchantIntegrationRepository.class);
    cipher = new SecretCipher("test-master-key-0123456789abcdef");
    verifier = new RazorpaySignatureVerifier(integrations, cipher);

    MerchantIntegration integration = new MerchantIntegration(UUID.randomUUID(), UUID.randomUUID(), "razorpay", "TEST");
    integration.setWebhookSecretEncrypted(cipher.encrypt(SECRET));
    when(integrations.findByProviderAndActiveTrue("razorpay")).thenReturn(List.of(integration));
  }

  @Test
  void validSignaturePasses() {
    String signature = RazorpaySignatureVerifier.hmacSha256(BODY.getBytes(StandardCharsets.UTF_8), SECRET);
    var result = verifier.verify(BODY.getBytes(StandardCharsets.UTF_8), signature);
    assertThat(result.valid()).isTrue();
  }

  @Test
  void tamperedBodyFails() {
    String signature = RazorpaySignatureVerifier.hmacSha256(BODY.getBytes(StandardCharsets.UTF_8), SECRET);
    var result = verifier.verify((BODY + " ").getBytes(StandardCharsets.UTF_8), signature);
    assertThat(result.valid()).isFalse();
  }

  @Test
  void wrongSignatureFails() {
    var result = verifier.verify(BODY.getBytes(StandardCharsets.UTF_8), "deadbeef");
    assertThat(result.valid()).isFalse();
  }

  @Test
  void missingSignatureFails() {
    var result = verifier.verify(BODY.getBytes(StandardCharsets.UTF_8), null);
    assertThat(result.valid()).isFalse();
  }

  @Test
  void hmacMatchesRazorpayDocumentedAlgorithm() {
    // Deterministic known-answer check for the HMAC-SHA256 hex implementation.
    assertThat(RazorpaySignatureVerifier.hmacSha256("hello".getBytes(StandardCharsets.UTF_8), "key"))
        .isEqualTo("9307b3b915efb5171ff14d8cb55fbcc798c6c0ef1456d66ded1a6aa723a58b7b");
  }
}
