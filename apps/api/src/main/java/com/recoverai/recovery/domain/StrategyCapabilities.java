package com.recoverai.recovery.domain;

import java.util.Set;

/** Only capabilities backed by concrete adapters are eligible for automatic selection. */
public final class StrategyCapabilities {
  private StrategyCapabilities() {}
  public static final Set<String> EXECUTABLE = Set.of("NO_ACTION", "PAYMENT_LINK", "UPI_RECOVERY", "DELAYED_RETRY",
      "EMAIL_NUDGE", "SMS_NUDGE", "WHATSAPP_NUDGE", "WAIT_FOR_PROVIDER_RETRY");
  public static boolean executable(String strategy) { return EXECUTABLE.contains(strategy); }
  public static boolean contactsCustomer(String strategy) {
    return Set.of("PAYMENT_LINK", "UPI_RECOVERY", "EMAIL_NUDGE", "SMS_NUDGE", "WHATSAPP_NUDGE").contains(strategy);
  }
}
