-- ============================================================================
-- RecoverAI — V2: strategy catalog + analytics view
-- ============================================================================

INSERT INTO recovery_strategies (code, name, description, requires_adapter, requires_approval, cost_minor_base) VALUES
  ('WAIT_FOR_PROVIDER_RETRY',     'Wait for provider retry',     'Let the payment provider run its own platform-managed retry cycle; monitor only.', NULL, FALSE, 0),
  ('DELAYED_RETRY',               'Delayed retry',               'Re-attempt the payment at a scheduled, customer-friendly time.', 'razorpay', FALSE, 0),
  ('PAYMENT_LINK',                'Payment link',                'Send a secure payment link the customer can complete on their own time.', 'razorpay', FALSE, 100),
  ('ALTERNATE_PAYMENT_METHOD',    'Alternate payment method',    'Encourage the customer to retry with a different payment method.', 'razorpay', FALSE, 0),
  ('UPI_RECOVERY',                'UPI recovery',                'Recover via UPI payment link or UPI intent.', 'razorpay', FALSE, 100),
  ('EMAIL_NUDGE',                 'Email nudge',                 'A polite, bounded email reminder with a secure payment link.', 'email', FALSE, 50),
  ('WHATSAPP_NUDGE',              'WhatsApp nudge',              'A polite WhatsApp reminder with a secure payment link.', 'whatsapp', FALSE, 100),
  ('SMS_NUDGE',                   'SMS nudge',                   'A short SMS reminder with a secure payment link.', 'sms', FALSE, 150),
  ('BOUNDED_DISCOUNT',            'Bounded discount',            'A policy-bounded discount to recover higher-value payments.', 'razorpay', TRUE, 200),
  ('PROMISE_TO_PAY',              'Promise to pay',              'Capture an explicit customer promise and follow up durably.', NULL, FALSE, 0),
  ('MANUAL_ESCALATION',           'Manual escalation',           'Escalate to a human operator for handling.', NULL, TRUE, 0),
  ('NO_ACTION',                   'No action',                   'Do nothing; incident is not economically recoverable.', NULL, FALSE, 0);

-- Strategy performance view (analytics read path)
CREATE OR REPLACE VIEW v_strategy_stats AS
SELECT
  a.org_id,
  a.strategy,
  count(*)                                              AS uses,
  count(*) FILTER (WHERE a.result = 'RECOVERED')        AS successes,
  coalesce(sum(r.recovered_amount_minor), 0)            AS gross_recovered_minor,
  coalesce(sum(r.net_recovered_minor), 0)               AS net_recovered_minor,
  coalesce(avg(r.intervention_cost_minor), 0)           AS avg_intervention_cost_minor,
  coalesce(avg(extract(epoch FROM (r.recovered_at - r.detected_at)) / 3600.0), 0) AS avg_time_to_recovery_hours
FROM recovery_actions a
JOIN revenue_incidents r ON r.id = a.incident_id
GROUP BY a.org_id, a.strategy;
