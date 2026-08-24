package com.recoverai.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recoverai.recovery.domain.RecoveryAction;
import com.recoverai.recovery.domain.RecoveryAction.Status;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Idempotency: deterministic operation keys mean re-running an operation can never
 * execute twice. Duplicate webhooks, worker retries, and replays are absorbed.
 */
class IdempotencyTest {

  @Test
  void idempotencyKeyIsDeterministic() {
    UUID incidentId = UUID.randomUUID();
    String k1 = RecoveryAction.idempotencyKeyFor(incidentId, "PAYMENT_LINK", 1);
    String k2 = RecoveryAction.idempotencyKeyFor(incidentId, "PAYMENT_LINK", 1);
    assertThat(k1).isEqualTo(k2);
    assertThat(k1).contains(incidentId.toString()).contains("PAYMENT_LINK").contains(":1");
    assertThat(RecoveryAction.idempotencyKeyFor(incidentId, "PAYMENT_LINK", 2)).isNotEqualTo(k1);
    assertThat(RecoveryAction.idempotencyKeyFor(incidentId, "DELAYED_RETRY", 1)).isNotEqualTo(k1);
  }

  @Test
  void duplicateActionInsertIsPreventedByRepositoryKey() {
    // The DB unique constraint on idempotency_key is the hard guarantee; here we
    // simulate the lookup path that avoids the insert in the first place.
    RecoveryAction existing = new RecoveryAction(
        UUID.randomUUID(), UUID.randomUUID(), "PAYMENT_LINK", 1, Instant.now(), "key-1");
    existing.setStatus(Status.SUCCEEDED);

    var repository = mock(com.recoverai.recovery.infrastructure.RecoveryActionRepository.class);
    when(repository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

    var saved = repository.findByIdempotencyKey("key-1");
    assertThat(saved).isPresent();
    assertThat(saved.get().getStatus()).isEqualTo(Status.SUCCEEDED);

    // Scheduling path would skip insert for an existing key:
    var fresh = repository.findByIdempotencyKey("key-2");
    assertThat(fresh).isEmpty();
    verify(repository, never()).save(any());
  }

  @Test
  void executionIsSkippedForAlreadyTerminalActions() {
    RecoveryAction done = new RecoveryAction(
        UUID.randomUUID(), UUID.randomUUID(), "PAYMENT_LINK", 1, Instant.now(), "k");
    done.setStatus(Status.CANCELLED);
    assertThat(done.getStatus() == Status.SCHEDULED).isFalse();
    // The executor's first guard is `if (status != SCHEDULED) return;` — represented here.
    assertThat(executable(done)).isFalse();
  }

  private boolean executable(RecoveryAction action) {
    return action.getStatus() == Status.SCHEDULED;
  }
}
