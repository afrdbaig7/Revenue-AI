package com.recoverai.admin;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.recoverai.admin.api.DlqAdminController;
import com.recoverai.audit.application.AuditService;
import com.recoverai.auth.api.AuthController;
import com.recoverai.common.api.ApiException;
import com.recoverai.common.tenant.CurrentUser;
import com.recoverai.outbox.domain.OutboxEvent;
import com.recoverai.outbox.infrastructure.OutboxEventRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class TenantAdministrationTest {
  @Test void listOnlyReadsCurrentOrganizationsEventsAndDoesNotExposePayloads() {
    UUID org = UUID.randomUUID();
    var repo = mock(OutboxEventRepository.class);
    var event = new OutboxEvent();
    event.setId(UUID.randomUUID());
    event.setEventType("payment-events:payment.failed");
    when(repo.findByOrgIdAndStatus(eq(org), eq("DEAD"), any())).thenReturn(new PageImpl<>(List.of(event)));
    var controller = new DlqAdminController(repo, mock(AuditService.class));
    var result = controller.list(auth(org), 0, 25);
    assertThat(result.items()).hasSize(1);
    verify(repo, never()).findByStatus(any(), any());
  }

  @Test void cannotReplayAnotherOrganizationsId() {
    UUID org = UUID.randomUUID(), foreignId = UUID.randomUUID();
    var repo = mock(OutboxEventRepository.class);
    var controller = new DlqAdminController(repo, mock(AuditService.class));
    assertThatThrownBy(() -> controller.replay(auth(org), foreignId)).isInstanceOf(ApiException.class);
    verify(repo, never()).resetForTenantReplay(any(), any(), any());
  }

  private UsernamePasswordAuthenticationToken auth(UUID org) {
    return new UsernamePasswordAuthenticationToken(new CurrentUser(UUID.randomUUID(), org, "admin@example.test", "Admin", "ADMIN"), null);
  }
}
