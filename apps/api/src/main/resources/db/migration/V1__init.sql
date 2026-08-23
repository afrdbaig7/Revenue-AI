-- ============================================================================
-- RecoverAI — V1: core schema
-- Conventions: UUID PKs, money as BIGINT minor units + CHAR(3) currency,
-- tenant scoping via org_id on every merchant-owned table, CHECK constraints
-- on canonical state machines, optimistic locking via version columns.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------------
-- Identity & tenancy
-- ---------------------------------------------------------------------------
CREATE TABLE organizations (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name        VARCHAR(160) NOT NULL,
  slug        VARCHAR(64)  NOT NULL UNIQUE,
  plan        VARCHAR(32)  NOT NULL DEFAULT 'FREE',
  status      VARCHAR(24)  NOT NULL DEFAULT 'ACTIVE',
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE users (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email         VARCHAR(320) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  full_name     VARCHAR(160) NOT NULL,
  status        VARCHAR(24)  NOT NULL DEFAULT 'ACTIVE',
  last_login_at TIMESTAMPTZ,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE memberships (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id     UUID NOT NULL REFERENCES organizations(id),
  user_id    UUID NOT NULL REFERENCES users(id),
  role       VARCHAR(24) NOT NULL CHECK (role IN ('OWNER','ADMIN','OPERATOR','ANALYST','VIEWER')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (org_id, user_id)
);

CREATE TABLE refresh_tokens (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id       UUID NOT NULL REFERENCES organizations(id),
  user_id      UUID NOT NULL REFERENCES users(id),
  token_hash   VARCHAR(64) NOT NULL UNIQUE,
  family_id    UUID NOT NULL,
  expires_at   TIMESTAMPTZ NOT NULL,
  revoked_at   TIMESTAMPTZ,
  replaced_by  UUID,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- ---------------------------------------------------------------------------
-- Merchant & integrations
-- ---------------------------------------------------------------------------
CREATE TABLE merchants (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id     UUID NOT NULL REFERENCES organizations(id),
  name       VARCHAR(160) NOT NULL,
  status     VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  currency   VARCHAR(3) NOT NULL DEFAULT 'INR',
  country    VARCHAR(2) NOT NULL DEFAULT 'IN',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_merchants_org ON merchants(org_id);

CREATE TABLE merchant_integrations (
  id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id                  UUID NOT NULL REFERENCES organizations(id),
  merchant_id             UUID NOT NULL REFERENCES merchants(id),
  provider                VARCHAR(32) NOT NULL,
  mode                    VARCHAR(16) NOT NULL CHECK (mode IN ('TEST','LIVE')),
  key_id_encrypted        TEXT,
  key_secret_encrypted    TEXT,
  webhook_secret_encrypted TEXT NOT NULL,
  status                  VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  is_active               BOOLEAN NOT NULL DEFAULT TRUE,
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (merchant_id, provider, mode)
);
CREATE INDEX idx_integrations_org ON merchant_integrations(org_id);

-- ---------------------------------------------------------------------------
-- Customers
-- ---------------------------------------------------------------------------
CREATE TABLE customers (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id                UUID NOT NULL REFERENCES organizations(id),
  merchant_id           UUID NOT NULL REFERENCES merchants(id),
  customer_ref          VARCHAR(128),
  email                 VARCHAR(320),
  phone                 VARCHAR(32),
  full_name             VARCHAR(160),
  segment               VARCHAR(32),
  opt_out_at            TIMESTAMPTZ,
  opt_out_reason        VARCHAR(32),
  preferred_channel     VARCHAR(16),
  preferred_time_window VARCHAR(16),
  contact_count         INT NOT NULL DEFAULT 0,
  last_contacted_at     TIMESTAMPTZ,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_customers_org_created ON customers(org_id, created_at DESC);
CREATE INDEX idx_customers_merchant_ref ON customers(merchant_id, customer_ref);
CREATE INDEX idx_customers_email ON customers(email);

-- ---------------------------------------------------------------------------
-- Payments (normalized, provider-agnostic)
-- ---------------------------------------------------------------------------
CREATE TABLE payments (
  id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id                   UUID NOT NULL REFERENCES organizations(id),
  merchant_id              UUID NOT NULL REFERENCES merchants(id),
  customer_id              UUID REFERENCES customers(id),
  provider                 VARCHAR(32) NOT NULL,
  provider_payment_id      VARCHAR(64),
  provider_order_id        VARCHAR(64),
  provider_account_reference VARCHAR(64),
  amount_minor             BIGINT NOT NULL CHECK (amount_minor > 0),
  currency                 VARCHAR(3) NOT NULL DEFAULT 'INR',
  status                   VARCHAR(32) NOT NULL DEFAULT 'CREATED'
                           CHECK (status IN ('CREATED','PENDING','AUTHORIZED','CAPTURED','FAILED','REFUNDED','PARTIALLY_REFUNDED')),
  payment_method           VARCHAR(32),
  failure_category         VARCHAR(40),
  failure_code             VARCHAR(64),
  failure_reason           VARCHAR(255),
  provider_failure_details JSONB,
  description              VARCHAR(255),
  captured_at              TIMESTAMPTZ,
  failed_at                TIMESTAMPTZ,
  created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  version                  BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_payments_provider_payment ON payments(provider, provider_payment_id) WHERE provider_payment_id IS NOT NULL;
CREATE INDEX idx_payments_org_created ON payments(org_id, created_at DESC);
CREATE INDEX idx_payments_customer ON payments(customer_id, created_at DESC);
CREATE INDEX idx_payments_status ON payments(status, updated_at);

CREATE TABLE payment_attempts (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id              UUID NOT NULL REFERENCES organizations(id),
  payment_id          UUID NOT NULL REFERENCES payments(id),
  attempt_no          INT NOT NULL,
  provider_attempt_id VARCHAR(64),
  amount_minor        BIGINT NOT NULL,
  currency            VARCHAR(3) NOT NULL DEFAULT 'INR',
  result              VARCHAR(32) NOT NULL,
  failure_category    VARCHAR(40),
  failure_reason      VARCHAR(255),
  raw_details         JSONB,
  attempted_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (payment_id, attempt_no)
);
CREATE INDEX idx_payment_attempts_payment ON payment_attempts(payment_id, attempted_at DESC);

-- ---------------------------------------------------------------------------
-- Subscriptions & checkouts
-- ---------------------------------------------------------------------------
CREATE TABLE subscriptions (
  id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id                 UUID NOT NULL REFERENCES organizations(id),
  merchant_id            UUID NOT NULL REFERENCES merchants(id),
  customer_id            UUID REFERENCES customers(id),
  provider_subscription_id VARCHAR(64),
  provider_plan_id       VARCHAR(64),
  status                 VARCHAR(32) NOT NULL,
  cadence                VARCHAR(24),
  amount_minor           BIGINT NOT NULL CHECK (amount_minor > 0),
  currency               VARCHAR(3) NOT NULL DEFAULT 'INR',
  current_period_start   TIMESTAMPTZ,
  current_period_end     TIMESTAMPTZ,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  version                BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_subscriptions_provider ON subscriptions(provider_subscription_id) WHERE provider_subscription_id IS NOT NULL;
CREATE INDEX idx_subscriptions_org ON subscriptions(org_id, created_at DESC);
CREATE INDEX idx_subscriptions_customer ON subscriptions(customer_id);

CREATE TABLE checkout_sessions (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id                UUID NOT NULL REFERENCES organizations(id),
  merchant_id           UUID NOT NULL REFERENCES merchants(id),
  customer_id           UUID REFERENCES customers(id),
  provider_session_id   VARCHAR(64),
  status                VARCHAR(32) NOT NULL DEFAULT 'CART_CREATED',
  amount_minor          BIGINT NOT NULL CHECK (amount_minor > 0),
  currency              VARCHAR(3) NOT NULL DEFAULT 'INR',
  cart_ref              VARCHAR(64),
  abandoned_at          TIMESTAMPTZ,
  resumed_at            TIMESTAMPTZ,
  completed_at          TIMESTAMPTZ,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  version               BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_checkouts_org ON checkout_sessions(org_id, created_at DESC);
CREATE INDEX idx_checkouts_provider ON checkout_sessions(provider_session_id) WHERE provider_session_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Webhook inbox (mission-critical ingestion ledger)
-- ---------------------------------------------------------------------------
CREATE TABLE webhook_inbox (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id            UUID,
  provider          VARCHAR(32) NOT NULL,
  provider_event_id VARCHAR(128) NOT NULL,
  event_type        VARCHAR(96) NOT NULL,
  payload_hash      VARCHAR(64) NOT NULL,
  raw_payload       JSONB,
  received_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed_at      TIMESTAMPTZ,
  processing_status VARCHAR(24) NOT NULL DEFAULT 'RECEIVED',
  retry_count       INT NOT NULL DEFAULT 0,
  error             VARCHAR(500),
  UNIQUE (provider, provider_event_id)
);
CREATE INDEX idx_webhook_inbox_status ON webhook_inbox(processing_status, received_at);
CREATE INDEX idx_webhook_inbox_org ON webhook_inbox(org_id, received_at DESC);

-- ---------------------------------------------------------------------------
-- Revenue incidents — the core workflow entity
-- ---------------------------------------------------------------------------
CREATE TABLE revenue_incidents (
  id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id                  UUID NOT NULL REFERENCES organizations(id),
  merchant_id             UUID NOT NULL REFERENCES merchants(id),
  customer_id             UUID REFERENCES customers(id),
  payment_id              UUID REFERENCES payments(id),
  subscription_id         UUID REFERENCES subscriptions(id),
  checkout_session_id     UUID REFERENCES checkout_sessions(id),
  incident_type           VARCHAR(32) NOT NULL
                          CHECK (incident_type IN ('PAYMENT_FAILURE','SUBSCRIPTION_FAILURE','CHECKOUT_ABANDONMENT','PROMISE_TO_PAY')),
  status                  VARCHAR(32) NOT NULL DEFAULT 'DETECTED'
                          CHECK (status IN ('DETECTED','RECONCILING','DIAGNOSING','STRATEGY_SELECTED','POLICY_EVALUATING',
                                            'AWAITING_APPROVAL','SCHEDULED','EXECUTING','RECOVERED','RETRYABLE_FAILURE',
                                            'FAILED','ESCALATED','BLOCKED','OPTED_OUT','EXPIRED','CANCELLED',
                                            'LATE_AUTHORIZED','CLOSED')),
  amount_minor            BIGINT NOT NULL CHECK (amount_minor >= 0),
  currency                VARCHAR(3) NOT NULL DEFAULT 'INR',
  failure_category        VARCHAR(40),
  diagnosis_confidence    NUMERIC(5,4),
  diagnosis_layer         VARCHAR(16),
  selected_strategy       VARCHAR(40),
  attempts_count          INT NOT NULL DEFAULT 0,
  contact_count           INT NOT NULL DEFAULT 0,
  recovered_amount_minor  BIGINT NOT NULL DEFAULT 0,
  intervention_cost_minor BIGINT NOT NULL DEFAULT 0,
  net_recovered_minor     BIGINT NOT NULL DEFAULT 0,
  recovery_window_ends_at TIMESTAMPTZ,
  next_action_at          TIMESTAMPTZ,
  detected_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  diagnosed_at            TIMESTAMPTZ,
  scheduled_at            TIMESTAMPTZ,
  executed_at             TIMESTAMPTZ,
  recovered_at            TIMESTAMPTZ,
  closed_at               TIMESTAMPTZ,
  cancellation_reason     VARCHAR(255),
  policy_result           VARCHAR(32),
  evidence_summary        JSONB,
  experiment_arm          VARCHAR(16),
  version                 BIGINT NOT NULL DEFAULT 0,
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_incidents_tenant_created ON revenue_incidents(org_id, created_at DESC);
CREATE INDEX idx_incidents_tenant_status  ON revenue_incidents(org_id, status);
CREATE INDEX idx_incidents_next_action    ON revenue_incidents(next_action_at) WHERE next_action_at IS NOT NULL;
CREATE INDEX idx_incidents_payment        ON revenue_incidents(payment_id);
CREATE INDEX idx_incidents_subscription   ON revenue_incidents(subscription_id);
CREATE INDEX idx_incidents_checkout       ON revenue_incidents(checkout_session_id);
CREATE INDEX idx_incidents_customer       ON revenue_incidents(customer_id, created_at DESC);

CREATE TABLE incident_diagnoses (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id             UUID NOT NULL REFERENCES organizations(id),
  incident_id        UUID NOT NULL REFERENCES revenue_incidents(id),
  layer              VARCHAR(16) NOT NULL CHECK (layer IN ('DETERMINISTIC','AI','HYBRID')),
  failure_category   VARCHAR(40) NOT NULL,
  confidence         NUMERIC(5,4) NOT NULL,
  source             VARCHAR(32) NOT NULL,
  evidence           JSONB NOT NULL DEFAULT '[]',
  recommended_action VARCHAR(40),
  model_version      VARCHAR(64),
  prompt_version     VARCHAR(64),
  raw_input_redacted TEXT,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_diagnoses_incident ON incident_diagnoses(incident_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- Recovery strategies (catalog) / decisions / actions / attempts
-- ---------------------------------------------------------------------------
CREATE TABLE recovery_strategies (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code             VARCHAR(40) NOT NULL UNIQUE,
  name             VARCHAR(120) NOT NULL,
  description      VARCHAR(500),
  requires_adapter VARCHAR(40),
  requires_approval BOOLEAN NOT NULL DEFAULT FALSE,
  cost_minor_base  BIGINT NOT NULL DEFAULT 0,
  is_active        BOOLEAN NOT NULL DEFAULT TRUE,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE recovery_decisions (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id           UUID NOT NULL REFERENCES organizations(id),
  incident_id      UUID NOT NULL REFERENCES revenue_incidents(id),
  candidates       JSONB NOT NULL,
  chosen_strategy  VARCHAR(40) NOT NULL,
  reason           VARCHAR(500),
  confidence       NUMERIC(5,4),
  ranking_source   VARCHAR(16) NOT NULL,
  model_version    VARCHAR(64),
  prompt_version   VARCHAR(64),
  policy_result    VARCHAR(32),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_decisions_incident ON recovery_decisions(incident_id, created_at DESC);

CREATE TABLE recovery_actions (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id             UUID NOT NULL REFERENCES organizations(id),
  incident_id        UUID NOT NULL REFERENCES revenue_incidents(id),
  strategy           VARCHAR(40) NOT NULL,
  status             VARCHAR(24) NOT NULL DEFAULT 'SCHEDULED'
                     CHECK (status IN ('SCHEDULED','EXECUTING','SUCCEEDED','FAILED','CANCELLED','BLOCKED','SKIPPED')),
  attempt_number     INT NOT NULL DEFAULT 1,
  scheduled_for      TIMESTAMPTZ NOT NULL,
  executed_at        TIMESTAMPTZ,
  idempotency_key    VARCHAR(190) NOT NULL UNIQUE,
  provider_reference VARCHAR(190),
  provider_response  JSONB,
  result             VARCHAR(64),
  error              VARCHAR(500),
  version            BIGINT NOT NULL DEFAULT 0,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_actions_scheduled ON recovery_actions(status, scheduled_for) WHERE status = 'SCHEDULED';
CREATE INDEX idx_actions_incident ON recovery_actions(incident_id, created_at DESC);

CREATE TABLE recovery_attempts (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id              UUID NOT NULL REFERENCES organizations(id),
  incident_id         UUID NOT NULL REFERENCES revenue_incidents(id),
  action_id           UUID REFERENCES recovery_actions(id),
  attempt_no          INT NOT NULL,
  strategy            VARCHAR(40) NOT NULL,
  status              VARCHAR(24) NOT NULL,
  outcome             VARCHAR(64),
  recovered_amount_minor BIGINT NOT NULL DEFAULT 0,
  occurred_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_attempts_incident ON recovery_attempts(incident_id, occurred_at DESC);

-- ---------------------------------------------------------------------------
-- Communications
-- ---------------------------------------------------------------------------
CREATE TABLE communications (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id            UUID NOT NULL REFERENCES organizations(id),
  incident_id       UUID REFERENCES revenue_incidents(id),
  customer_id       UUID NOT NULL REFERENCES customers(id),
  channel           VARCHAR(16) NOT NULL CHECK (channel IN ('EMAIL','SMS','WHATSAPP','DEMO_INBOX','PUSH')),
  template          VARCHAR(64),
  subject           VARCHAR(255),
  body_redacted     TEXT NOT NULL,
  status            VARCHAR(24) NOT NULL DEFAULT 'QUEUED'
                    CHECK (status IN ('QUEUED','SENT','FAILED','BLOCKED','OPTED_OUT','SIMULATED')),
  simulated         BOOLEAN NOT NULL DEFAULT FALSE,
  provider_message_id VARCHAR(120),
  sent_at           TIMESTAMPTZ,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_comm_tenant_created ON communications(org_id, created_at DESC);
CREATE INDEX idx_comm_incident ON communications(incident_id);

-- ---------------------------------------------------------------------------
-- Promise to pay
-- ---------------------------------------------------------------------------
CREATE TABLE promises_to_pay (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id             UUID NOT NULL REFERENCES organizations(id),
  incident_id        UUID NOT NULL REFERENCES revenue_incidents(id),
  customer_id        UUID NOT NULL REFERENCES customers(id),
  promised_amount_minor BIGINT NOT NULL CHECK (promised_amount_minor > 0),
  currency           VARCHAR(3) NOT NULL DEFAULT 'INR',
  promised_at        TIMESTAMPTZ NOT NULL,
  preferred_time     VARCHAR(32),
  preferred_channel  VARCHAR(16),
  status             VARCHAR(24) NOT NULL DEFAULT 'PROMISED'
                     CHECK (status IN ('PROMISED','SCHEDULED','DUE','FULFILLED','MISSED','CANCELLED')),
  confidence         NUMERIC(5,4),
  source             VARCHAR(32),
  fulfilled_at       TIMESTAMPTZ,
  version            BIGINT NOT NULL DEFAULT 0,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_promises_incident ON promises_to_pay(incident_id);
CREATE INDEX idx_promises_due ON promises_to_pay(status, promised_at) WHERE status IN ('PROMISED','SCHEDULED');

-- ---------------------------------------------------------------------------
-- Policies
-- ---------------------------------------------------------------------------
CREATE TABLE policy_sets (
  id                            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id                        UUID NOT NULL REFERENCES organizations(id),
  merchant_id                   UUID REFERENCES merchants(id),
  name                          VARCHAR(120) NOT NULL,
  is_active                     BOOLEAN NOT NULL DEFAULT TRUE,
  max_retries                   INT NOT NULL DEFAULT 3,
  max_contact_attempts          INT NOT NULL DEFAULT 2,
  max_discount_percent          INT NOT NULL DEFAULT 10,
  recovery_window_hours         INT NOT NULL DEFAULT 72,
  minimum_recoverable_amount    BIGINT NOT NULL DEFAULT 10000,
  contact_cooldown_hours        INT NOT NULL DEFAULT 12,
  require_approval_above_amount BIGINT NOT NULL DEFAULT 1000000,
  allow_whatsapp                BOOLEAN NOT NULL DEFAULT TRUE,
  allow_email                   BOOLEAN NOT NULL DEFAULT TRUE,
  allow_sms                     BOOLEAN NOT NULL DEFAULT TRUE,
  allow_discounts               BOOLEAN NOT NULL DEFAULT TRUE,
  allow_payment_links           BOOLEAN NOT NULL DEFAULT TRUE,
  allow_delayed_retry           BOOLEAN NOT NULL DEFAULT TRUE,
  version                       BIGINT NOT NULL DEFAULT 0,
  created_at                    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_policy_sets_org ON policy_sets(org_id, is_active);

CREATE TABLE policy_rules (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id        UUID NOT NULL REFERENCES organizations(id),
  policy_set_id UUID NOT NULL REFERENCES policy_sets(id),
  rule_type     VARCHAR(64) NOT NULL,
  params        JSONB NOT NULL DEFAULT '{}',
  enabled       BOOLEAN NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Approvals (human-in-the-loop)
-- ---------------------------------------------------------------------------
CREATE TABLE approvals (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id        UUID NOT NULL REFERENCES organizations(id),
  incident_id   UUID NOT NULL REFERENCES revenue_incidents(id),
  requested_by  UUID REFERENCES users(id),
  proposal      JSONB NOT NULL,
  status        VARCHAR(24) NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED','CANCELLED')),
  decided_by    UUID REFERENCES users(id),
  decided_at    TIMESTAMPTZ,
  decision_note VARCHAR(500),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_approvals_org_status ON approvals(org_id, status);
CREATE INDEX idx_approvals_incident ON approvals(incident_id);

-- ---------------------------------------------------------------------------
-- Audit ledger (append-only)
-- ---------------------------------------------------------------------------
CREATE TABLE audit_events (
  id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id                   UUID NOT NULL REFERENCES organizations(id),
  incident_id              UUID,
  entity_type              VARCHAR(64) NOT NULL,
  entity_id                VARCHAR(64),
  actor_type               VARCHAR(24) NOT NULL,
  actor_id                 VARCHAR(64),
  event_type               VARCHAR(64) NOT NULL,
  timestamp                TIMESTAMPTZ NOT NULL DEFAULT now(),
  correlation_id           VARCHAR(64),
  trace_id                 VARCHAR(64),
  previous_state           VARCHAR(40),
  new_state                VARCHAR(40),
  decision_input_snapshot  JSONB,
  decision_output_snapshot JSONB,
  metadata                 JSONB
);
CREATE INDEX idx_audit_tenant_incident ON audit_events(org_id, incident_id, timestamp DESC);
CREATE INDEX idx_audit_tenant_created ON audit_events(org_id, timestamp DESC);
CREATE INDEX idx_audit_entity ON audit_events(entity_type, entity_id);

-- ---------------------------------------------------------------------------
-- Transactional outbox
-- ---------------------------------------------------------------------------
CREATE TABLE outbox_events (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id           UUID NOT NULL REFERENCES organizations(id),
  aggregate_type   VARCHAR(64) NOT NULL,
  aggregate_id     VARCHAR(64) NOT NULL,
  event_type       VARCHAR(64) NOT NULL,
  payload          JSONB NOT NULL,
  correlation_id   VARCHAR(64),
  status           VARCHAR(24) NOT NULL DEFAULT 'PENDING'
                   CHECK (status IN ('PENDING','PUBLISHED','FAILED','DEAD')),
  attempts         INT NOT NULL DEFAULT 0,
  next_attempt_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at     TIMESTAMPTZ,
  last_error       VARCHAR(500),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_outbox_status ON outbox_events(status, next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_org ON outbox_events(org_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- Experiments
-- ---------------------------------------------------------------------------
CREATE TABLE experiments (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id            UUID NOT NULL REFERENCES organizations(id),
  name              VARCHAR(160) NOT NULL,
  description       VARCHAR(500),
  seed              BIGINT NOT NULL,
  population_size   INT NOT NULL,
  baseline_config   JSONB NOT NULL,
  treatment_config  JSONB NOT NULL,
  status            VARCHAR(24) NOT NULL DEFAULT 'RUNNING',
  results           JSONB,
  report_format     VARCHAR(16),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at      TIMESTAMPTZ
);
CREATE INDEX idx_experiments_org ON experiments(org_id, created_at DESC);

CREATE TABLE experiment_assignments (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id            UUID NOT NULL REFERENCES organizations(id),
  experiment_id     UUID NOT NULL REFERENCES experiments(id),
  incident_key      VARCHAR(64) NOT NULL,
  arm               VARCHAR(16) NOT NULL CHECK (arm IN ('CONTROL','TREATMENT')),
  amount_minor      BIGINT NOT NULL,
  currency          VARCHAR(3) NOT NULL DEFAULT 'INR',
  failure_category  VARCHAR(40),
  recovered         BOOLEAN NOT NULL DEFAULT FALSE,
  recovered_amount_minor BIGINT NOT NULL DEFAULT 0,
  attempts          INT NOT NULL DEFAULT 0,
  contacts          INT NOT NULL DEFAULT 0,
  time_to_recovery_hours NUMERIC(8,2),
  policy_blocks     INT NOT NULL DEFAULT 0,
  UNIQUE (experiment_id, incident_key, arm)
);
CREATE INDEX idx_experiment_assignments ON experiment_assignments(experiment_id);

-- ---------------------------------------------------------------------------
-- Metric snapshots (analytics without hot-table scans)
-- ---------------------------------------------------------------------------
CREATE TABLE metric_snapshots (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id        UUID NOT NULL REFERENCES organizations(id),
  period_start  TIMESTAMPTZ NOT NULL,
  period_end    TIMESTAMPTZ NOT NULL,
  metrics       JSONB NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (org_id, period_start)
);
CREATE INDEX idx_snapshots_org ON metric_snapshots(org_id, period_start DESC);
