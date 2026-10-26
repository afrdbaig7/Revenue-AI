package com.recoverai.analytics.application;

import com.recoverai.common.api.ApiException;
import com.recoverai.incident.domain.RevenueIncident;
import com.recoverai.payment.domain.Payment;
import com.recoverai.payment.domain.PaymentStatus;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Confirmed captures are counted once; authorizations and generated artifacts are not revenue. */
@Service
@RequiredArgsConstructor
public class SettlementLedger {
  private final JdbcTemplate jdbc;
  private final jakarta.persistence.EntityManager entityManager;
  private final com.recoverai.audit.application.AuditService audit;

  @Transactional
  public boolean capture(UUID orgId, RevenueIncident incident, Payment payment) {
    if (!orgId.equals(incident.getOrgId()) || !orgId.equals(payment.getOrgId())
        || !incident.getMerchantId().equals(payment.getMerchantId()) || payment.getStatus() != PaymentStatus.CAPTURED
        || payment.getAmountMinor() != incident.getAmountMinor() || !payment.getCurrency().equals(incident.getCurrency())) {
      throw ApiException.conflict("Capture does not match the recovery incident");
    }
    entityManager.flush(); // JDBC foreign keys must see incident/payment inserts made by JPA in this transaction.
    boolean inserted = jdbc.update("""
        INSERT INTO recovery_settlements(org_id, incident_id, payment_id, amount_minor, currency, refunded_minor)
        VALUES (?, ?, ?, ?, ?, LEAST(?, (SELECT coalesce(sum(amount_minor), 0) FROM recovery_refunds WHERE org_id = ? AND payment_id = ?)))
        ON CONFLICT DO NOTHING
        """, orgId, incident.getId(), payment.getId(), payment.getAmountMinor(), payment.getCurrency(), payment.getAmountMinor(), orgId, payment.getId()) > 0;
    if (!inserted) {
      var existing = jdbc.query("SELECT payment_id FROM recovery_settlements WHERE org_id = ? AND incident_id = ?", (rs, row) -> rs.getObject(1, UUID.class), orgId, incident.getId());
      if (!existing.isEmpty() && !existing.get(0).equals(payment.getId())) {
        audit.record("DUPLICATE_CAPTURE_DETECTED", "payment", payment.getId().toString(), incident.getId(), null, "CAPTURED", null, null, null);
      }
    }
    return inserted;
  }

  @Transactional
  public void refund(UUID orgId, UUID paymentId, long amountMinor) {
    jdbc.update("UPDATE recovery_settlements SET refunded_minor = LEAST(amount_minor, GREATEST(refunded_minor, ?)) WHERE org_id = ? AND payment_id = ?", amountMinor, orgId, paymentId);
  }

  @Transactional
  public void refundEvent(UUID orgId, UUID paymentId, String refundId, long amountMinor) {
    if (amountMinor <= 0 || refundId.isBlank()) throw ApiException.badRequest("Invalid refund event");
    jdbc.update("""
        WITH recorded AS (
          INSERT INTO recovery_refunds(org_id, provider_refund_id, payment_id, amount_minor)
          VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING RETURNING amount_minor
        ) UPDATE recovery_settlements SET refunded_minor = LEAST(amount_minor, refunded_minor + (SELECT amount_minor FROM recorded))
          WHERE org_id = ? AND payment_id = ? AND EXISTS (SELECT 1 FROM recorded)
        """, orgId, refundId, paymentId, amountMinor, orgId, paymentId);
  }

  public long recovered(UUID orgId) {
    Long result = jdbc.queryForObject("SELECT coalesce(sum(amount_minor - refunded_minor), 0) FROM recovery_settlements WHERE org_id = ? AND currency = 'INR'", Long.class, orgId);
    return result == null ? 0 : result;
  }

  public long recoveredIncidents(UUID orgId) {
    Long result = jdbc.queryForObject("SELECT count(DISTINCT incident_id) FROM recovery_settlements WHERE org_id = ? AND amount_minor > refunded_minor", Long.class, orgId);
    return result == null ? 0 : result;
  }

  public long recoveredForIncident(UUID orgId, UUID incidentId) {
    Long result = jdbc.queryForObject("SELECT coalesce(sum(amount_minor - refunded_minor), 0) FROM recovery_settlements WHERE org_id = ? AND incident_id = ?", Long.class, orgId, incidentId);
    return result == null ? 0 : result;
  }
}
