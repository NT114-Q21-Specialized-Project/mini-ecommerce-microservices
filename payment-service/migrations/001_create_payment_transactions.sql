CREATE TABLE IF NOT EXISTS payment_transactions (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    user_id UUID,
    amount DOUBLE PRECISION NOT NULL,
    currency VARCHAR(16) NOT NULL,
    operation_type VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider_ref VARCHAR(120),
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    correlation_id VARCHAR(120),
    failure_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_order_created
ON payment_transactions (order_id, created_at DESC);
