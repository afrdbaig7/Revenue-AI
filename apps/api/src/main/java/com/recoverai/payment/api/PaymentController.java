package com.recoverai.payment.api;

import com.recoverai.common.api.ApiException;
import com.recoverai.common.api.PageResponse;
import com.recoverai.common.tenant.CurrentUser;
import com.recoverai.payment.domain.Payment;
import com.recoverai.payment.infrastructure.PaymentRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentRepository payments;

  @GetMapping
  public PageResponse<Payment> list(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    UUID orgId = ((CurrentUser) authentication.getPrincipal()).orgId();
    Page<Payment> result = payments.findAll(
        (root, query, cb) -> cb.equal(root.get("orgId"), orgId),
        PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt")));
    return PageResponse.of(result.getContent(), page, Math.min(size, 100), result.getTotalElements());
  }

  @GetMapping("/{id}")
  public Payment get(Authentication authentication, @PathVariable UUID id) {
    UUID orgId = ((CurrentUser) authentication.getPrincipal()).orgId();
    return payments
        .findByOrgIdAndId(orgId, id)
        .orElseThrow(() -> ApiException.notFound("Payment not found"));
  }
}
