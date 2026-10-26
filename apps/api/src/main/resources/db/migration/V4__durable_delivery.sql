-- Additive delivery metadata. Historical FAILED rows require an operator-reviewed replay.
ALTER TABLE outbox_events ADD COLUMN claim_token UUID;
ALTER TABLE outbox_events ADD COLUMN lease_until TIMESTAMPTZ;
CREATE INDEX idx_outbox_claim ON outbox_events(next_attempt_at, created_at)
  WHERE status IN ('PENDING', 'PROCESSING');
CREATE TABLE processed_events (
  org_id UUID NOT NULL REFERENCES organizations(id),
  event_id UUID NOT NULL,
  handler VARCHAR(64) NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(org_id, event_id, handler)
);
ALTER TABLE webhook_inbox DROP CONSTRAINT webhook_inbox_provider_provider_event_id_key;
ALTER TABLE webhook_inbox ADD CONSTRAINT uq_webhook_tenant_event UNIQUE(org_id, provider, provider_event_id);
CREATE INDEX idx_webhook_tenant_status ON webhook_inbox(org_id, processing_status, received_at);
CREATE INDEX idx_outbox_tenant_status ON outbox_events(org_id, status, created_at);
CREATE INDEX idx_promises_tenant_incident ON promises_to_pay(org_id, incident_id);
