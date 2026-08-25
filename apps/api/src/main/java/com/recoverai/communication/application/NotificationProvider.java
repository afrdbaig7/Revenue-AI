package com.recoverai.communication.application;

import com.recoverai.communication.domain.Communication;
import com.recoverai.communication.domain.Communication.Channel;

/**
 * Notification provider abstraction. Real adapters (Email/SMS/WhatsApp) implement this
 * interface; the demo provider renders into the in-app communication inbox and is always
 * labeled SIMULATED — a simulated send is never presented as a real one.
 */
public interface NotificationProvider {

  /** The channel this provider handles (or {@code DEMO_INBOX} for the demo adapter). */
  Channel channel();

  /** Provider name for logs/audit (e.g. "razorpay-sms", "demo-inbox"). */
  String providerName();

  /**
   * Send a message. Implementations MUST be safe for the caller to retry: the caller
   * supplies a unique idempotency reference which providers use for provider-side dedup.
   *
   * @return provider-side message id
   */
  SendResult send(Communication message, String idempotencyRef);

  /** @return true when real credentials are configured (false = simulated adapter). */
  boolean isReal();

  record SendResult(String providerMessageId, boolean delivered, String detail) {}
}
