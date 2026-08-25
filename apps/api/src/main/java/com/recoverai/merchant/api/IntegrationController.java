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

  @GetMapping
  public List<IntegrationView> list(Authentication authentication) {
    UUID orgId = ((CurrentUser) authentication.getPrincipal()).orgId();
    return integrations.findByOrgIdAndActiveTrue(orgId).stream().map(this::toView).toList();
  }

  @PostMapping("/razorpay/test")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public IntegrationView configureTest(
      Authentication authentication, @Valid @RequestBody RazorpayTestRequest req) {
    UUID orgId = ((CurrentUser) authentication.getPrincipal()).orgId();
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
    return toView(integrations.save(integration));
  }

  @PostMapping("/{id}/toggle")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public IntegrationView toggle(Authentication authentication, @PathVariable UUID id) {
    UUID orgId = ((CurrentUser) authentication.getPrincipal()).orgId();
    MerchantIntegration integration = integrations.findById(id)
        .filter(i -> i.getOrgId().equals(orgId))
        .orElseThrow(() -> ApiException.notFound("Integration not found"));
    integration.setActive(!integration.isActive());
    return toView(integrations.save(integration));
  }

  private IntegrationView toView(MerchantIntegration i) {
    return new IntegrationView(
        i.getId(),
        i.getProvider(),
        i.getMode(),
        i.isActive(),
        i.getStatus(),
        i.getKeyIdEncrypted() == null ? null : "encrypted:" + i.getKeyIdEncrypted().substring(0, Math.min(8, i.getKeyIdEncrypted().length())) + "...",
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
