-- ============================================================================
-- RecoverAI — V3: currency columns to VARCHAR(3)
-- Postgres CHAR(3) (bpchar) breaks Hibernate strict schema validation for
-- String-mapped fields; VARCHAR(3) is equivalent for ISO-4217 codes.
-- ============================================================================
ALTER TABLE payments            ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE payment_attempts    ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE subscriptions       ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE checkout_sessions   ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE revenue_incidents   ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE promises_to_pay     ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE experiment_assignments ALTER COLUMN currency TYPE VARCHAR(3);

ALTER TABLE merchants ALTER COLUMN country TYPE VARCHAR(2);
ALTER TABLE webhook_inbox ALTER COLUMN payload_hash TYPE VARCHAR(64);
