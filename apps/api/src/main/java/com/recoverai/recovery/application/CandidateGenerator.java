package com.recoverai.recovery.application;

import com.recoverai.incident.domain.IncidentType;
import com.recoverai.incident.domain.RevenueIncident;
import com.recoverai.payment.domain.FailureCategory;
import com.recoverai.payment.domain.Payment;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Deterministic eligibility generation: which strategies MAY apply to this incident,
 * based on failure category, payment method, incident type and time of day. The AI may
 * rank among these; it may not invent new ones.
 */
@Component
public class CandidateGenerator {

  public record CandidateSeed(String strategy, String rationale) {}

  public List<CandidateSeed> generate(RevenueIncident incident, Payment payment) {
    Set<CandidateSeed> seeds = new LinkedHashSet<>();
    FailureCategory category = categoryOf(incident, payment);
    int hour = java.time.ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("Asia/Kolkata")).getHour();
    boolean businessHours = hour >= 9 && hour <= 21;

    switch (incident.getIncidentType()) {
      case CHECKOUT_ABANDONMENT -> {
        seeds.add(new CandidateSeed("EMAIL_NUDGE", "Abandoned checkout — a gentle email with a payment link recovers best"));
        seeds.add(new CandidateSeed("WHATSAPP_NUDGE", "High-open-rate channel for this segment"));
        seeds.add(new CandidateSeed("PAYMENT_LINK", "Direct secure link removes friction"));
        seeds.add(new CandidateSeed("PROMISE_TO_PAY", "Customer may prefer to pay later"));
      }
      case SUBSCRIPTION_FAILURE -> {
        if (category == FailureCategory.MANDATE_CANCELLED || category == FailureCategory.MANDATE_FAILURE) {
          seeds.add(new CandidateSeed("PAYMENT_LINK", "Mandate broken — a payment link bypasses the mandate"));
          seeds.add(new CandidateSeed("ALTERNATE_PAYMENT_METHOD", "Ask customer to re-add a payment method"));
        } else {
          seeds.add(new CandidateSeed("WAIT_FOR_PROVIDER_RETRY", "Razorpay retries subscription charges automatically"));
          seeds.add(new CandidateSeed("DELAYED_RETRY", "Retry after the platform-managed retry window"));
        }
        seeds.add(new CandidateSeed("EMAIL_NUDGE", "Subscription context — email is the least intrusive channel"));
        seeds.add(new CandidateSeed("PROMISE_TO_PAY", "Customer may commit to paying on a specific day"));
      }
      default -> {
        if (category.isTransient()) {
          seeds.add(new CandidateSeed("DELAYED_RETRY", "Transient failure — time heals it; retry at a friendly hour"));
          seeds.add(new CandidateSeed("WAIT_FOR_PROVIDER_RETRY", "Let the platform retry cycle run first"));
          if (businessHours) {
            seeds.add(new CandidateSeed("PAYMENT_LINK", "Customer can complete immediately if they prefer"));
          }
        }
        if (category.requiresCustomerAction()) {
          seeds.add(new CandidateSeed("PAYMENT_LINK", category + " requires customer action — a link lets them act now"));
          seeds.add(new CandidateSeed("ALTERNATE_PAYMENT_METHOD", "A different method may succeed where this one failed"));
        }
        if (category == FailureCategory.BANK_DECLINE || category == FailureCategory.CARD_BLOCKED) {
          seeds.add(new CandidateSeed("ALTERNATE_PAYMENT_METHOD", "Bank declined this method; another method may work"));
        }
        if (incident.getAmountMinor() > 500_00) { // > ₹500
          seeds.add(new CandidateSeed("EMAIL_NUDGE", "Higher-value payment justifies one polite contact"));
        }
        seeds.add(new CandidateSeed("PROMISE_TO_PAY", "Customer may commit to paying on a specific day"));
      }
    }
    seeds.add(new CandidateSeed("NO_ACTION", "Always available: incident not economically recoverable"));
    return new ArrayList<>(seeds);
  }

  private FailureCategory categoryOf(RevenueIncident incident, Payment payment) {
    if (incident.getFailureCategory() != null) {
      try {
        return FailureCategory.valueOf(incident.getFailureCategory());
      } catch (IllegalArgumentException ignored) {
        // fall through
      }
    }
    if (payment != null && payment.getFailureCategory() != null) {
      return payment.getFailureCategory();
    }
    return FailureCategory.UNKNOWN;
  }
}
