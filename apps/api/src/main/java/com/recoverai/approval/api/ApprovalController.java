package com.recoverai.approval.api;

import com.recoverai.approval.application.ApprovalService;
import com.recoverai.approval.domain.Approval;
import com.recoverai.approval.infrastructure.ApprovalRepository;
import com.recoverai.common.api.PageResponse;
import com.recoverai.common.tenant.CurrentUser;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

/** Human-in-the-loop approval queue. */
@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
public class ApprovalController {

  private final ApprovalRepository approvals;
  private final ApprovalService approvalService;

  @GetMapping
  public PageResponse<Approval> list(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size,
      @RequestParam(defaultValue = "PENDING") String status) {
    UUID orgId = ((CurrentUser) authentication.getPrincipal()).orgId();
    Page<Approval> result = approvals.findByOrgIdAndStatusOrderByCreatedAtAsc(orgId, Approval.Status.valueOf(status), PageRequest.of(page, Math.min(size, 100)));
    return PageResponse.of(result.getContent(), page, Math.min(size, 100), result.getTotalElements());
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
  public Approval approve(Authentication authentication, @PathVariable UUID id, @RequestBody(required = false) DecisionRequest req) {
    CurrentUser user = (CurrentUser) authentication.getPrincipal();
    return approvalService.approve(user.orgId(), id, user, req == null ? null : req.note());
  }

  @PostMapping("/{id}/reject")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
  public Approval reject(Authentication authentication, @PathVariable UUID id, @RequestBody(required = false) DecisionRequest req) {
    CurrentUser user = (CurrentUser) authentication.getPrincipal();
    return approvalService.reject(user.orgId(), id, user, req == null ? null : req.note());
  }

  public record DecisionRequest(@Size(max = 500) String note) {}
}
