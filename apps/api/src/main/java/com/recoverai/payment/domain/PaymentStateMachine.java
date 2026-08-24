package com.recoverai.payment.domain;

import com.recoverai.common.api.ApiException;
import com.recoverai.common.api.ErrorCode;
import java.util.EnumSet;
import java.util.Set;

/**
 * Validated payment state machine. Provider webhooks arrive out of order, so FAILED is
 * allowed to move to AUTHORIZED/CAPTURED (late authorization) — this is the dangerous
 * race that recovery must handle, never reject.
 */
public final class PaymentStateMachine {

  private PaymentStateMachine() {}

  private static final Set<PaymentStatus> FROM_FAILED_ALLOWED =
      EnumSet.of(PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURED, PaymentStatus.FAILED);

  public static PaymentStatus transition(PaymentStatus from, PaymentStatus to) {
    if (from == to) {
      return to;
    }
    boolean valid = switch (from) {
      case CREATED -> to == PaymentStatus.PENDING || to == PaymentStatus.FAILED || to == PaymentStatus.AUTHORIZED;
      case PENDING -> to == PaymentStatus.AUTHORIZED || to == PaymentStatus.FAILED || to == PaymentStatus.CAPTURED;
      case AUTHORIZED -> to == PaymentStatus.CAPTURED || to == PaymentStatus.FAILED;
      case CAPTURED -> to == PaymentStatus.REFUNDED || to == PaymentStatus.PARTIALLY_REFUNDED;
      case PARTIALLY_REFUNDED -> to == PaymentStatus.REFUNDED;
      case FAILED -> FROM_FAILED_ALLOWED.contains(to);
      case REFUNDED -> false;
    };
    if (!valid) {
      throw new ApiException(
          ErrorCode.PAYMENT_STATE_INVALID,
          "Invalid payment transition " + from + " -> " + to,
          409);
    }
    return to;
  }

  /** True when this transition represents a late authorization after an apparent failure. */
  public static boolean isLateAuthorization(PaymentStatus from, PaymentStatus to) {
    return from == PaymentStatus.FAILED && (to == PaymentStatus.AUTHORIZED || to == PaymentStatus.CAPTURED);
  }
}
