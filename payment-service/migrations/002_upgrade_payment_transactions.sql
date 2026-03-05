-- Upgrade script for existing databases initialized before BigDecimal/refund guard changes.
-- This script is idempotent and safe to run multiple times.

ALTER TABLE payment_transactions
    ADD COLUMN IF NOT EXISTS reference_payment_id UUID;

ALTER TABLE payment_transactions
    ALTER COLUMN amount TYPE NUMERIC(19, 2)
    USING ROUND(amount::numeric, 2);

UPDATE payment_transactions
SET operation_type = UPPER(operation_type)
WHERE operation_type IS NOT NULL
  AND operation_type <> UPPER(operation_type);

UPDATE payment_transactions
SET status = CASE
    WHEN UPPER(status) = 'PAY' THEN 'PAID'
    WHEN UPPER(status) = 'REFUND' THEN 'REFUNDED'
    ELSE UPPER(status)
END
WHERE status IS NOT NULL
  AND status <> CASE
    WHEN UPPER(status) = 'PAY' THEN 'PAID'
    WHEN UPPER(status) = 'REFUND' THEN 'REFUNDED'
    ELSE UPPER(status)
END;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = 'payment_transactions'::regclass
          AND c.contype = 'f'
          AND pg_get_constraintdef(c.oid) LIKE 'FOREIGN KEY (reference_payment_id) REFERENCES payment_transactions(id)%'
    ) THEN
        ALTER TABLE payment_transactions
            ADD CONSTRAINT fk_payment_reference_payment
            FOREIGN KEY (reference_payment_id) REFERENCES payment_transactions(id);
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = 'payment_transactions'::regclass
          AND c.contype = 'c'
          AND c.conname = 'chk_payment_operation_type'
    ) THEN
        ALTER TABLE payment_transactions
            ADD CONSTRAINT chk_payment_operation_type
            CHECK (operation_type IN ('PAY', 'REFUND'));
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = 'payment_transactions'::regclass
          AND c.contype = 'c'
          AND c.conname = 'chk_payment_status'
    ) THEN
        ALTER TABLE payment_transactions
            ADD CONSTRAINT chk_payment_status
            CHECK (status IN ('PAID', 'REFUNDED', 'FAILED'));
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_payment_order_created
    ON payment_transactions (order_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_payment_refund_lookup
    ON payment_transactions (reference_payment_id, status);
