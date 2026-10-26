-- NOT VALID avoids silently repairing historical tenant relationships. New writes are checked.
CREATE UNIQUE INDEX uq_merchants_tenant_id ON merchants(org_id, id);
CREATE UNIQUE INDEX uq_customers_tenant_id ON customers(org_id, id);
CREATE UNIQUE INDEX uq_payments_tenant_id ON payments(org_id, id);
CREATE UNIQUE INDEX uq_incidents_tenant_id ON revenue_incidents(org_id, id);
CREATE UNIQUE INDEX uq_actions_tenant_id ON recovery_actions(org_id, id);
ALTER TABLE merchant_integrations ADD CONSTRAINT fk_integration_tenant_merchant FOREIGN KEY(org_id, merchant_id) REFERENCES merchants(org_id, id) NOT VALID;
ALTER TABLE customers ADD CONSTRAINT fk_customer_tenant_merchant FOREIGN KEY(org_id, merchant_id) REFERENCES merchants(org_id, id) NOT VALID;
ALTER TABLE payments ADD CONSTRAINT fk_payment_tenant_merchant FOREIGN KEY(org_id, merchant_id) REFERENCES merchants(org_id, id) NOT VALID;
ALTER TABLE payments ADD CONSTRAINT fk_payment_tenant_customer FOREIGN KEY(org_id, customer_id) REFERENCES customers(org_id, id) NOT VALID;
ALTER TABLE revenue_incidents ADD CONSTRAINT fk_incident_tenant_merchant FOREIGN KEY(org_id, merchant_id) REFERENCES merchants(org_id, id) NOT VALID;
ALTER TABLE revenue_incidents ADD CONSTRAINT fk_incident_tenant_payment FOREIGN KEY(org_id, payment_id) REFERENCES payments(org_id, id) NOT VALID;
ALTER TABLE revenue_incidents ADD CONSTRAINT fk_incident_tenant_customer FOREIGN KEY(org_id, customer_id) REFERENCES customers(org_id, id) NOT VALID;
ALTER TABLE recovery_actions ADD CONSTRAINT fk_action_tenant_incident FOREIGN KEY(org_id, incident_id) REFERENCES revenue_incidents(org_id, id) NOT VALID;
ALTER TABLE recovery_attempts ADD CONSTRAINT fk_attempt_tenant_incident FOREIGN KEY(org_id, incident_id) REFERENCES revenue_incidents(org_id, id) NOT VALID;
ALTER TABLE communications ADD CONSTRAINT fk_communication_tenant_incident FOREIGN KEY(org_id, incident_id) REFERENCES revenue_incidents(org_id, id) NOT VALID;
ALTER TABLE communications ADD CONSTRAINT fk_communication_tenant_customer FOREIGN KEY(org_id, customer_id) REFERENCES customers(org_id, id) NOT VALID;
ALTER TABLE approvals ADD CONSTRAINT fk_approval_tenant_incident FOREIGN KEY(org_id, incident_id) REFERENCES revenue_incidents(org_id, id) NOT VALID;
ALTER TABLE promises_to_pay ADD CONSTRAINT fk_promise_tenant_incident FOREIGN KEY(org_id, incident_id) REFERENCES revenue_incidents(org_id, id) NOT VALID;
ALTER TABLE recovery_settlements ADD CONSTRAINT fk_settlement_tenant_incident FOREIGN KEY(org_id, incident_id) REFERENCES revenue_incidents(org_id, id) NOT VALID;
ALTER TABLE recovery_settlements ADD CONSTRAINT fk_settlement_tenant_payment FOREIGN KEY(org_id, payment_id) REFERENCES payments(org_id, id) NOT VALID;
CREATE TABLE recovery_refunds (
  org_id UUID NOT NULL REFERENCES organizations(id),
  provider_refund_id VARCHAR(128) NOT NULL,
  payment_id UUID NOT NULL,
  amount_minor BIGINT NOT NULL CHECK(amount_minor > 0),
  PRIMARY KEY(org_id, provider_refund_id),
  FOREIGN KEY(org_id, payment_id) REFERENCES payments(org_id, id)
);
ALTER TABLE outbox_events ALTER COLUMN aggregate_id TYPE VARCHAR(128);
