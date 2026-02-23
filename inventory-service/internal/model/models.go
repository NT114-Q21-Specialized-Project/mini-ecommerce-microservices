package model

import "time"

type InventoryOperation struct {
	ID             string    `json:"id"`
	IdempotencyKey string    `json:"idempotencyKey"`
	OperationType  string    `json:"operationType"`
	OrderID        string    `json:"orderId"`
	ProductID      string    `json:"productId"`
	Quantity       int       `json:"quantity"`
	Status         string    `json:"status"`
	ErrorCode      string    `json:"errorCode,omitempty"`
	ErrorMessage   string    `json:"errorMessage,omitempty"`
	CorrelationID  string    `json:"correlationId"`
	CreatedAt      time.Time `json:"createdAt"`
}

type ReserveRequest struct {
	OrderID        string `json:"orderId"`
	ProductID      string `json:"productId"`
	Quantity       int    `json:"quantity"`
	IdempotencyKey string `json:"idempotencyKey,omitempty"`
}

type ReleaseRequest struct {
	OrderID        string `json:"orderId"`
	ProductID      string `json:"productId"`
	Quantity       int    `json:"quantity"`
	IdempotencyKey string `json:"idempotencyKey,omitempty"`
}

type InventoryView struct {
	ProductID      string `json:"productId"`
	AvailableStock int    `json:"availableStock"`
	Source         string `json:"source"`
}

type ServiceError struct {
	Code       string `json:"code"`
	Message    string `json:"message"`
	HTTPStatus int    `json:"-"`
}
