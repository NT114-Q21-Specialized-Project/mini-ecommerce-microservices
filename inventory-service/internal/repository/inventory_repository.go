package repository

import (
	"database/sql"
	"time"

	"inventory-service/internal/model"
)

type InventoryRepository struct {
	db *sql.DB
}

func NewInventoryRepository(db *sql.DB) (*InventoryRepository, error) {
	repo := &InventoryRepository{db: db}
	if err := repo.initSchema(); err != nil {
		return nil, err
	}
	return repo, nil
}

func (r *InventoryRepository) initSchema() error {
	query := `
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
`
	_, err := r.db.Exec(query)
	return err
}

func (r *InventoryRepository) FindByIdempotencyKey(idempotencyKey string) (*model.InventoryOperation, error) {
	query := `
SELECT id, idempotency_key, operation_type, order_id, product_id, quantity,
       status, error_code, error_message, correlation_id, created_at
FROM inventory_operations
WHERE idempotency_key = $1
`
	row := r.db.QueryRow(query, idempotencyKey)
	return scanOperation(row)
}

func (r *InventoryRepository) FindByOrderAndType(orderID string, operationType string) (*model.InventoryOperation, error) {
	query := `
SELECT id, idempotency_key, operation_type, order_id, product_id, quantity,
       status, error_code, error_message, correlation_id, created_at
FROM inventory_operations
WHERE order_id = $1
  AND operation_type = $2
`
	row := r.db.QueryRow(query, orderID, operationType)
	return scanOperation(row)
}

func (r *InventoryRepository) ClaimOperation(operation *model.InventoryOperation) (bool, error) {
	query := `
INSERT INTO inventory_operations (
    id,
    idempotency_key,
    operation_type,
    order_id,
    product_id,
    quantity,
    status,
    error_code,
    error_message,
    correlation_id,
    created_at
) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)
ON CONFLICT DO NOTHING
`
	result, err := r.db.Exec(
		query,
		operation.ID,
		operation.IdempotencyKey,
		operation.OperationType,
		operation.OrderID,
		operation.ProductID,
		operation.Quantity,
		operation.Status,
		nullIfEmpty(operation.ErrorCode),
		nullIfEmpty(operation.ErrorMessage),
		nullIfEmpty(operation.CorrelationID),
		operation.CreatedAt,
	)
	if err != nil {
		return false, err
	}

	affected, err := result.RowsAffected()
	if err != nil {
		return false, err
	}

	return affected == 1, nil
}

func (r *InventoryRepository) UpdateOperationResult(
	idempotencyKey string,
	status string,
	errorCode string,
	errorMessage string,
	correlationID string,
) error {
	query := `
UPDATE inventory_operations
SET status = $2,
    error_code = $3,
    error_message = $4,
    correlation_id = $5
WHERE idempotency_key = $1
`
	_, err := r.db.Exec(
		query,
		idempotencyKey,
		status,
		nullIfEmpty(errorCode),
		nullIfEmpty(errorMessage),
		nullIfEmpty(correlationID),
	)
	return err
}

func scanOperation(row *sql.Row) (*model.InventoryOperation, error) {
	operation := &model.InventoryOperation{}
	var orderID sql.NullString
	var errorCode sql.NullString
	var errorMessage sql.NullString
	var correlationID sql.NullString
	var createdAt time.Time

	err := row.Scan(
		&operation.ID,
		&operation.IdempotencyKey,
		&operation.OperationType,
		&orderID,
		&operation.ProductID,
		&operation.Quantity,
		&operation.Status,
		&errorCode,
		&errorMessage,
		&correlationID,
		&createdAt,
	)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}

	if orderID.Valid {
		operation.OrderID = orderID.String
	}
	if errorCode.Valid {
		operation.ErrorCode = errorCode.String
	}
	if errorMessage.Valid {
		operation.ErrorMessage = errorMessage.String
	}
	if correlationID.Valid {
		operation.CorrelationID = correlationID.String
	}
	operation.CreatedAt = createdAt

	return operation, nil
}

func nullIfEmpty(value string) any {
	if value == "" {
		return nil
	}
	return value
}
