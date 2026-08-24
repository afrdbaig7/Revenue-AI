package com.recoverai.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recoverai.common.api.ApiException;
import com.recoverai.payment.domain.FailureCategory;
import com.recoverai.payment.domain.FailureTaxonomy;
import com.recoverai.payment.domain.PaymentStateMachine;
import com.recoverai.payment.domain.PaymentStatus;
import org.junit.jupiter.api.Test;

/** Payment state machine + failure taxonomy — correctness of the money-state model. */
class PaymentStateMachineTest {

  @Test
  void normalLifecycle() {
    assertThat(PaymentStateMachine.transition(PaymentStatus.CREATED, PaymentStatus.PENDING))
        .isEqualTo(PaymentStatus.PENDING);
    assertThat(PaymentStateMachine.transition(PaymentStatus.PENDING, PaymentStatus.AUTHORIZED))
        .isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(PaymentStateMachine.transition(PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURED))
        .isEqualTo(PaymentStatus.CAPTURED);
    assertThat(PaymentStateMachine.transition(PaymentStatus.CAPTURED, PaymentStatus.REFUNDED))
        .isEqualTo(PaymentStatus.REFUNDED);
  }

  @Test
  void lateAuthorizationIsAllowedFromFailed() {
    // The dangerous race: failed → authorized arrives late. Must be legal so the
    // platform can cancel recovery instead of double-collecting.
    assertThat(PaymentStateMachine.transition(PaymentStatus.FAILED, PaymentStatus.AUTHORIZED))
        .isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(PaymentStateMachine.isLateAuthorization(PaymentStatus.FAILED, PaymentStatus.AUTHORIZED))
        .isTrue();
    assertThat(PaymentStateMachine.transition(PaymentStatus.FAILED, PaymentStatus.CAPTURED))
        .isEqualTo(PaymentStatus.CAPTURED);
  }

  @Test
  void capturedCannotBecomeFailed() {
    assertThatThrownBy(() -> PaymentStateMachine.transition(PaymentStatus.CAPTURED, PaymentStatus.FAILED))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void refundedIsTerminal() {
    assertThatThrownBy(() -> PaymentStateMachine.transition(PaymentStatus.REFUNDED, PaymentStatus.CAPTURED))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void failureTaxonomyMapsKnownCodes() {
    assertThat(FailureTaxonomy.classify("INSUFFICIENT_FUNDS", "card"))
        .isEqualTo(FailureCategory.INSUFFICIENT_FUNDS);
    assertThat(FailureTaxonomy.classify("CARD_EXPIRED", "card"))
        .isEqualTo(FailureCategory.CARD_EXPIRED);
    assertThat(FailureTaxonomy.classify("MANDATE_CANCELLED", "upi"))
        .isEqualTo(FailureCategory.MANDATE_CANCELLED);
    assertThat(FailureTaxonomy.classify("NETWORK_TIMEOUT", "card"))
        .isEqualTo(FailureCategory.NETWORK_TIMEOUT);
    assertThat(FailureTaxonomy.classify("OTP_VERIFICATION_FAILED", "card"))
        .isEqualTo(FailureCategory.AUTHENTICATION_FAILURE);
  }

  @Test
  void unknownCodesMapToUnknownNotCrash() {
    assertThat(FailureTaxonomy.classify("SOME_WEIRD_NEW_CODE", "card"))
        .isEqualTo(FailureCategory.UNKNOWN);
    assertThat(FailureTaxonomy.classify(null, null)).isEqualTo(FailureCategory.UNKNOWN);
  }

  @Test
  void verboseDescriptionsAreHeuristicMatched() {
    assertThat(FailureTaxonomy.classify("The bank reported insufficient funds", "card"))
        .isEqualTo(FailureCategory.INSUFFICIENT_FUNDS);
  }

  @Test
  void transientCategoriesHealWithTime() {
    assertThat(FailureCategory.INSUFFICIENT_FUNDS.isTransient()).isTrue();
    assertThat(FailureCategory.CARD_EXPIRED.isTransient()).isFalse();
    assertThat(FailureCategory.CARD_EXPIRED.requiresCustomerAction()).isTrue();
  }
}
