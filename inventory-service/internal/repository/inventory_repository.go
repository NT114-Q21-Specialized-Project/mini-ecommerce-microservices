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

func (r *InventoryRepository) CreateOperation(operation *model.InventoryOperation) error {
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
`
	_, err := r.db.Exec(
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
	return err
}

func nullIfEmpty(value string) any {
	if value == "" {
		return nil
	}
	return value
}
