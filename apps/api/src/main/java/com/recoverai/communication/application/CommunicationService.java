package com.recoverai.communication.application;

import com.recoverai.common.api.ApiException;
import com.recoverai.common.util.RateLimiter;
import com.recoverai.communication.domain.Communication;
import com.recoverai.communication.domain.Communication.Channel;
import com.recoverai.communication.domain.Communication.Status;
import com.recoverai.communication.infrastructure.CommunicationRepository;
import com.recoverai.customer.domain.Customer;
import com.recoverai.customer.infrastructure.CustomerRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Communication engine: template rendering, channel preferences, opt-out enforcement,
 * contact cooldowns, maximum attempts, and provider dispatch. AI-generated text is
 * constrained (fixed templates + bounded personalization) and never bypasses these rules.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommunicationService {

  private final CommunicationRepository repository;
  private final CustomerRepository customers;
  private final NotificationProvider notificationProvider;
  private final RateLimiter rateLimiter;
  private final com.recoverai.incident.infrastructure.RevenueIncidentRepository incidents;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final com.recoverai.policy.infrastructure.PolicySetRepository policies;
  private final com.recoverai.policy.application.PolicyEngine policyEngine;

  @Transactional
  public Communication sendRecoveryMessage(
      UUID orgId,
      UUID incidentId,
      UUID customerId,
      Channel channel,
      String template,
      Map<String, String> vars,
      int contactsSoFar,
      int maxContacts,
      Duration cooldown) {

    String operationKey = "comm:" + vars.getOrDefault("_operationKey", incidentId + ":" + template + ":" + contactsSoFar);
    jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", (rs, row) -> 0, orgId + ":customer:" + customerId);
    var existing = repository.findByOrgIdAndOperationKey(orgId, operationKey);
    if (existing.isPresent()) return existing.get();
    Customer customer = customers
        .findById(customerId)
        .filter(c -> c.getOrgId().equals(orgId))
        .orElseThrow(() -> ApiException.notFound("Customer not found"));

    Communication message = new Communication();
    message.setOrgId(orgId);
    message.setOperationKey(operationKey);
    message.setIncidentId(incidentId);
    message.setCustomerId(customerId);
    message.setChannel(channel);
    message.setTemplate(template);
    message.setSubject(renderSubject(template, vars));
    message.setBodyRedacted(renderBody(template, vars));
    if (incidentId != null) {
      var current = incidents.findByIdForUpdate(incidentId).filter(i -> i.getOrgId().equals(orgId))
          .orElseThrow(() -> ApiException.notFound("Incident not found"));
      var policy = policies.findByOrgIdAndActiveTrue(orgId).orElseThrow(() -> ApiException.conflict("Active policy missing"));
      String strategy = vars.getOrDefault("_strategy", switch (channel) { case SMS -> "SMS_NUDGE"; case WHATSAPP -> "WHATSAPP_NUDGE"; default -> "EMAIL_NUDGE"; });
      boolean expired = current.getRecoveryWindowEndsAt() != null && !current.getRecoveryWindowEndsAt().isAfter(Instant.now());
      if (current.getStatus().isTerminal() || expired || !policyEngine.evaluate(policy, null, strategy,
          0, current.getContactCount(), Instant.now(), current.getAmountMinor()).allowed()) {
        message.setStatus(Status.BLOCKED);
        return repository.save(message);
      }
      contactsSoFar = current.getContactCount();
      maxContacts = policy.getMaxContactAttempts();
      cooldown = Duration.ofHours(policy.getContactCooldownHours());
    }

    // --- Hard rules: opt-out overrides everything (including AI recommendations) ---
    if (customer.optedOut()) {
      message.setStatus(Status.OPTED_OUT);
      log.info("COMMUNICATION_BLOCKED reason=OPTED_OUT customer={} incident={}", customerId, incidentId);
      return repository.save(message);
    }
    if (channel != Channel.DEMO_INBOX && customer.getPreferredChannel() != null
        && !customer.getPreferredChannel().equals(channel.name())) {
      message.setStatus(Status.BLOCKED);
      return repository.save(message);
    }
    if (contactsSoFar >= maxContacts) {
      message.setStatus(Status.BLOCKED);
      log.info("COMMUNICATION_BLOCKED reason=MAX_CONTACTS customer={} incident={}", customerId, incidentId);
      return repository.save(message);
    }
    if (customer.getLastContactedAt() != null
        && customer.getLastContactedAt().plus(cooldown).isAfter(Instant.now())) {
      message.setStatus(Status.BLOCKED);
      log.info("COMMUNICATION_BLOCKED reason=COOLDOWN customer={} incident={}", customerId, incidentId);
      return repository.save(message);
    }
    if (!rateLimiter.tryAcquire("comms:" + orgId, 500, Duration.ofMinutes(1))) {
      message.setStatus(Status.FAILED);
      message.setProviderMessageId("rate-limited");
      return repository.save(message);
    }

    NotificationProvider.SendResult result =
        notificationProvider.send(message, operationKey);
    if (result.delivered()) {
      message.setStatus(notificationProvider.isReal() ? Status.SENT : Status.SIMULATED);
      message.setSimulated(!notificationProvider.isReal());
      message.setSentAt(Instant.now());
      message.setProviderMessageId(result.providerMessageId());
      customer.recordContact();
      customers.save(customer);
      if (incidentId != null) {
        var incident = incidents.findByIdForUpdate(incidentId).filter(i -> i.getOrgId().equals(orgId))
            .orElseThrow(() -> ApiException.notFound("Incident not found"));
        incident.setContactCount(incident.getContactCount() + 1);
        incidents.save(incident);
      }
    } else {
      message.setStatus(Status.FAILED);
    }
    return repository.save(message);
  }

  private String renderSubject(String template, Map<String, String> vars) {
    return switch (template) {
      case "payment_failed_gentle" -> "Your payment needs a quick retry — " + vars.getOrDefault("merchantName", "");
      case "payment_link_ready" -> "Secure payment link for your pending payment";
      case "promise_reminder" -> "Reminder: your payment is due today";
      case "discount_incentive" -> "A small credit to help complete your payment";
      case "payment_method_update" -> "Update your payment method to continue";
      default -> "Your " + vars.getOrDefault("merchantName", "") + " payment";
    };
  }

  /**
   * Constrained body rendering. Personalization is limited to whitelisted variables; no
   * threats, no false urgency, no legal claims, no PII beyond the customer's own
   * name/amount. (AI drafts are only ever surfaced through approved templates.)
   */
  private String renderBody(String template, Map<String, String> vars) {
    String amount = vars.getOrDefault("amount", "");
    String name = vars.getOrDefault("customerName", "there");
    String merchant = vars.getOrDefault("merchantName", "");
    return switch (template) {
      case "payment_failed_gentle" ->
          "Hi " + name + ", your " + amount + " payment to " + merchant
              + " could not be completed. No action needed from you right now — we will "
              + "retry automatically, or you can complete it with a secure link at your convenience.";
      case "payment_link_ready" ->
          "Hi " + name + ", your " + amount + " payment to " + merchant
              + " is pending. Complete it securely here: " + vars.getOrDefault("paymentLink", "(link)")
              + " (valid for 72 hours).";
      case "promise_reminder" ->
          "Hi " + name + ", as promised, here is your secure link to complete the " + amount
              + " payment to " + merchant + ".";
      case "discount_incentive" ->
          "Hi " + name + ", complete your " + amount + " payment to " + merchant
              + " and a small credit will be applied automatically.";
      case "payment_method_update" ->
          "Hi " + name + ", your saved payment method for " + merchant
              + " could not be charged. Please update it to avoid any service interruption.";
      default -> "Hi " + name + ", regarding your payment to " + merchant + ".";
    };
  }
}
