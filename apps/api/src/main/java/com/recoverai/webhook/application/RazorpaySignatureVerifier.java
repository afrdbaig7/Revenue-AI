package com.recoverai.webhook.application;

import com.recoverai.merchant.application.SecretCipher;
import com.recoverai.merchant.domain.MerchantIntegration;
import com.recoverai.merchant.infrastructure.MerchantIntegrationRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Razorpay webhook signature verification: HMAC-SHA256 of the RAW request body with the
 * integration's webhook secret. Signature is verified BEFORE the payload is parsed or
 * trusted. A payload that matches no active integration secret is rejected.
 */
@Component
public class RazorpaySignatureVerifier {

  public static final String SIGNATURE_HEADER = "X-Razorpay-Signature";

  private final MerchantIntegrationRepository integrations;
  private final SecretCipher cipher;

  public RazorpaySignatureVerifier(MerchantIntegrationRepository integrations, SecretCipher cipher) {
    this.integrations = integrations;
    this.cipher = cipher;
  }

  /**
   * Verify the signature against the secrets of all active Razorpay integrations (demo
   * has one; bounded loop). Returns the matching integration or an invalid result.
   */
  public VerificationResult verify(byte[] rawBody, String signatureHeader) {
    if (signatureHeader == null || signatureHeader.isBlank() || rawBody == null) {
      return VerificationResult.invalid("missing signature header");
    }
    List<MerchantIntegration> candidates = integrations.findByProviderAndActiveTrue("razorpay");
    for (MerchantIntegration integration : candidates) {
      try {
        String secret = cipher.decrypt(integration.getWebhookSecretEncrypted());
        if (hmacSha256(rawBody, secret).equals(signatureHeader.trim())) {
          return VerificationResult.valid(integration);
        }
      } catch (Exception e) {
        // corrupted secret for one integration — skip it, don't fail closed on one row
      }
    }
    return VerificationResult.invalid("signature did not match any active integration");
  }

  public static String hmacSha256(byte[] data, String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] bytes = mac.doFinal(data);
      StringBuilder sb = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) {
        sb.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new IllegalStateException("HMAC-SHA256 unavailable", e);
    }
  }

  public record VerificationResult(boolean valid, MerchantIntegration integration, String reason) {
    public static VerificationResult valid(MerchantIntegration integration) {
      return new VerificationResult(true, integration, null);
    }

    public static VerificationResult invalid(String reason) {
      return new VerificationResult(false, null, reason);
    }
  }
}
