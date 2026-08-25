package com.recoverai.communication.api;

import com.recoverai.common.api.PageResponse;
import com.recoverai.common.tenant.CurrentUser;
import com.recoverai.communication.domain.Communication;
import com.recoverai.communication.infrastructure.CommunicationRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Customer communication inbox (demo notification provider renders here). */
@RestController
@RequestMapping("/api/v1/communications")
@RequiredArgsConstructor
public class CommunicationController {

  private final CommunicationRepository communications;

  @GetMapping
  public PageResponse<Communication> list(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    UUID orgId = ((CurrentUser) authentication.getPrincipal()).orgId();
    Page<Communication> result =
        communications.findByOrgIdOrderByCreatedAtDesc(orgId, PageRequest.of(page, Math.min(size, 100)));
    return PageResponse.of(result.getContent(), page, Math.min(size, 100), result.getTotalElements());
  }
}
