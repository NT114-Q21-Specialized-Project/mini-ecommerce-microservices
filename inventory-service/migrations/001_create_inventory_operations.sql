CREATE TABLE IF NOT EXISTS inventory_operations (
    id TEXT PRIMARY KEY,
    idempotency_key TEXT NOT NULL UNIQUE,
    operation_type TEXT NOT NULL,
    order_id TEXT,
    product_id TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    status TEXT NOT NULL,
    error_code TEXT,
    error_message TEXT,
    correlation_id TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_inventory_ops_product_created
ON inventory_operations (product_id, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_inventory_order_operation
ON inventory_operations (order_id, operation_type)
WHERE order_id IS NOT NULL;
