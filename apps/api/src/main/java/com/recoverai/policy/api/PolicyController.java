package com.recoverai.policy.api;

import com.recoverai.common.api.ApiException;
import com.recoverai.common.tenant.CurrentUser;
import com.recoverai.policy.domain.PolicySet;
import com.recoverai.policy.infrastructure.PolicySetRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Merchant-configurable bounded policy controls. */
@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class PolicyController {

  private final PolicySetRepository policySets;

  @GetMapping
  public List<PolicySet> list(Authentication authentication) {
    UUID orgId = ((CurrentUser) authentication.getPrincipal()).orgId();
    return policySets.findAll().stream().filter(p -> p.getOrgId().equals(orgId)).toList();
  }

  @GetMapping("/{id}")
  public PolicySet get(Authentication authentication, @PathVariable UUID id) {
    UUID orgId = ((CurrentUser) authentication.getPrincipal()).orgId();
    return policySets
        .findByOrgIdAndId(orgId, id)
        .orElseThrow(() -> ApiException.notFound("Policy set not found"));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public PolicySet update(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody PolicyUpdate req) {
    UUID orgId = ((CurrentUser) authentication.getPrincipal()).orgId();
    PolicySet policy = policySets
        .findByOrgIdAndId(orgId, id)
        .orElseThrow(() -> ApiException.notFound("Policy set not found"));
    policy.setMaxRetries(req.maxRetries());
    policy.setMaxContactAttempts(req.maxContactAttempts());
    policy.setMaxDiscountPercent(req.maxDiscountPercent());
    policy.setRecoveryWindowHours(req.recoveryWindowHours());
    policy.setMinimumRecoverableAmount(req.minimumRecoverableAmount());
    policy.setContactCooldownHours(req.contactCooldownHours());
    policy.setRequireApprovalAboveAmount(req.requireApprovalAboveAmount());
    policy.setAllowWhatsApp(req.allowWhatsApp());
    policy.setAllowEmail(req.allowEmail());
    policy.setAllowSms(req.allowSms());
    policy.setAllowDiscounts(req.allowDiscounts());
    policy.setAllowPaymentLinks(req.allowPaymentLinks());
    policy.setAllowDelayedRetry(req.allowDelayedRetry());
    policy.setName(req.name() == null ? policy.getName() : req.name());
    policy.setUpdatedAt(Instant.now());
    return policySets.save(policy);
  }

  public record PolicyUpdate(
      String name,
      @Min(0) @Max(10) int maxRetries,
      @Min(0) @Max(10) int maxContactAttempts,
      @Min(0) @Max(50) int maxDiscountPercent,
      @Min(1) @Max(720) int recoveryWindowHours,
      @Min(0) long minimumRecoverableAmount,
      @Min(0) @Max(168) int contactCooldownHours,
      @Min(0) long requireApprovalAboveAmount,
      boolean allowWhatsApp,
      boolean allowEmail,
      boolean allowSms,
      boolean allowDiscounts,
      boolean allowPaymentLinks,
      boolean allowDelayedRetry) {}
}
