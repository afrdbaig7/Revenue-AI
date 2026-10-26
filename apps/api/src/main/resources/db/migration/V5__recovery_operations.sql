ALTER TABLE recovery_actions ADD COLUMN lease_until TIMESTAMPTZ;
CREATE INDEX idx_actions_expired_lease ON recovery_actions(lease_until) WHERE status = 'EXECUTING';
ALTER TABLE communications ADD COLUMN operation_key VARCHAR(190);
CREATE UNIQUE INDEX uq_communication_operation ON communications(org_id, operation_key) WHERE operation_key IS NOT NULL;
DROP INDEX uq_payments_provider_payment;
CREATE UNIQUE INDEX uq_payments_tenant_provider ON payments(org_id, provider, provider_payment_id) WHERE provider_payment_id IS NOT NULL;
DROP INDEX uq_subscriptions_provider;
CREATE UNIQUE INDEX uq_subscriptions_tenant_provider ON subscriptions(org_id, provider_subscription_id) WHERE provider_subscription_id IS NOT NULL;
CREATE UNIQUE INDEX uq_checkout_tenant_provider ON checkout_sessions(org_id, provider_session_id) WHERE provider_session_id IS NOT NULL;
CREATE TABLE recovery_settlements (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id UUID NOT NULL REFERENCES organizations(id),
  incident_id UUID NOT NULL REFERENCES revenue_incidents(id),
  payment_id UUID NOT NULL REFERENCES payments(id),
  amount_minor BIGINT NOT NULL CHECK(amount_minor >= 0),
  refunded_minor BIGINT NOT NULL DEFAULT 0 CHECK(refunded_minor >= 0),
  currency VARCHAR(3) NOT NULL CHECK(currency = 'INR'),
  captured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(org_id, payment_id)
);
CREATE INDEX idx_settlement_tenant_incident ON recovery_settlements(org_id, incident_id);
