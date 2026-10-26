package com.recoverai.merchant.api;

import com.recoverai.common.api.ApiException;
import com.recoverai.common.config.RecoverAiProperties;
import com.recoverai.common.tenant.CurrentUser;
import com.recoverai.merchant.application.SecretCipher;
import com.recoverai.merchant.domain.Merchant;
import com.recoverai.merchant.domain.MerchantIntegration;
import com.recoverai.merchant.infrastructure.MerchantIntegrationRepository;
import com.recoverai.merchant.infrastructure.MerchantRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Integrations management — Razorpay TEST MODE only. Secrets are encrypted at rest and
 * never returned to the browser; the UI shows a masked reference + TEST MODE badge.
 */
@RestController
@RequestMapping("/api/v1/integrations")
@RequiredArgsConstructor
public class IntegrationController {

  private final MerchantIntegrationRepository integrations;
  private final MerchantRepository merchants;
  private final SecretCipher cipher;
  private final com.recoverai.audit.application.AuditService audit;
  private final com.recoverai.integration.razorpay.RazorpayPaymentProvider razorpay;

  @GetMapping
  public List<IntegrationView> list(Authentication authentication) {
    UUID orgId = ((CurrentUser) authentication.getPrincipal()).orgId();
    return integrations.findByOrgId(orgId).stream().map(this::toView).toList();
  }

  @PostMapping("/razorpay/test")
  @org.springframework.transaction.annotation.Transactional
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public IntegrationView configureTest(
      Authentication authentication, @Valid @RequestBody RazorpayTestRequest req) {
    UUID orgId = ((CurrentUser) authentication.getPrincipal()).orgId();
    if (!req.keyId().startsWith("rzp_test_")) throw ApiException.badRequest("Only Razorpay test keys are supported");
    Merchant merchant = merchants.findByOrgId(orgId).stream()
        .findFirst()
        .orElseThrow(() -> ApiException.conflict("No merchant configured for this organization"));

    MerchantIntegration integration = integrations
        .findByOrgIdAndProviderAndMode(orgId, "razorpay", "TEST")
        .orElseGet(() -> new MerchantIntegration(orgId, merchant.getId(), "razorpay", "TEST"));

    integration.setKeyIdEncrypted(req.keyId() == null || req.keyId().isBlank() ? integration.getKeyIdEncrypted() : cipher.encrypt(req.keyId()));
    integration.setKeySecretEncrypted(req.keySecret() == null || req.keySecret().isBlank() ? integration.getKeySecretEncrypted() : cipher.encrypt(req.keySecret()));
    integration.setWebhookSecretEncrypted(req.webhookSecret() == null || req.webhookSecret().isBlank() ? integration.getWebhookSecretEncrypted() : cipher.encrypt(req.webhookSecret()));
    integration.setMode("TEST");
    integration.setActive(true);
    integration.setStatus("ACTIVE");
    MerchantIntegration saved = integrations.save(integration);
    audit.record("INTEGRATION_CONFIGURED", "merchant_integration", saved.getId().toString());
    return toView(saved);
  }

  @PostMapping("/{id}/toggle")
  @org.springframework.transaction.annotation.Transactional
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public IntegrationView toggle(Authentication authentication, @PathVariable UUID id) {
    UUID orgId = ((CurrentUser) authentication.getPrincipal()).orgId();
    MerchantIntegration integration = integrations.findById(id)
        .filter(i -> i.getOrgId().equals(orgId))
        .orElseThrow(() -> ApiException.notFound("Integration not found"));
    integration.setActive(!integration.isActive());
    integration.setStatus(integration.isActive() ? "ACTIVE" : "DISABLED");
    audit.record("INTEGRATION_TOGGLED", "merchant_integration", id.toString());
    return toView(integrations.save(integration));
  }

  @PostMapping("/{id}/verify")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public Map<String, Object> verify(Authentication authentication, @PathVariable UUID id) {
    UUID orgId = ((CurrentUser) authentication.getPrincipal()).orgId();
    MerchantIntegration integration = integrations.findById(id).filter(i -> i.getOrgId().equals(orgId))
        .orElseThrow(() -> ApiException.notFound("Integration not found"));
    if (integration.getKeyIdEncrypted() == null || integration.getKeySecretEncrypted() == null) throw ApiException.badRequest("Configure test credentials first");
    try {
      razorpay.withCredentials(cipher.decrypt(integration.getKeyIdEncrypted()), cipher.decrypt(integration.getKeySecretEncrypted())).verifyConnection();
      audit.record("INTEGRATION_VERIFIED", "merchant_integration", id.toString());
      return Map.of("verified", true, "mode", "TEST");
    } catch (com.recoverai.integration.domain.PaymentProvider.ProviderException e) {
      throw new com.recoverai.common.api.ApiException(com.recoverai.common.api.ErrorCode.PROVIDER_UNAVAILABLE, "Connection verification failed: " + e.category(), 502);
    }
  }

  private IntegrationView toView(MerchantIntegration i) {
    return new IntegrationView(
        i.getId(),
        i.getProvider(),
        i.getMode(),
        i.isActive(),
        i.getStatus(),
        i.getKeyIdEncrypted() == null ? null : "rzp_test_••••",
        i.getWebhookSecretEncrypted() != null,
        "TEST MODE — no live transactions",
        i.getCreatedAt());
  }

  public record RazorpayTestRequest(
      @NotBlank String keyId,
      @NotBlank String keySecret,
      @NotBlank String webhookSecret) {}

  public record IntegrationView(
      UUID id,
      String provider,
      String mode,
      boolean active,
      String status,
      String keyIdMasked,
      boolean webhookSecretConfigured,
      String modeLabel,
      java.time.Instant createdAt) {}
}
