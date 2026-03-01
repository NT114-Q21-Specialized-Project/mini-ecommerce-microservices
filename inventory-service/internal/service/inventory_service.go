package service

import (
	"encoding/json"
	"fmt"
	"io"
	"math/rand"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"inventory-service/internal/config"
	"inventory-service/internal/middleware"
	"inventory-service/internal/model"
	"inventory-service/internal/repository"
)

const (
	operationReserve   = "RESERVE"
	operationRelease   = "RELEASE"
	statusInProgress   = "IN_PROGRESS"
	statusSuccess      = "SUCCESS"
	statusFailed       = "FAILED"
	serviceStatusResv  = "RESERVED"
	serviceStatusRel   = "RELEASED"
	httpConflictStatus = http.StatusConflict
)

type InventoryService struct {
	repo                  *repository.InventoryRepository
	httpClient            *http.Client
	productServiceBaseURL string
	chaosMode             bool
	latencyProbability    float64
	errorProbability      float64
	chaosDelayMs          int
}

type ProductView struct {
	ID    string  `json:"id"`
	Name  string  `json:"name"`
	Price float64 `json:"price"`
	Stock int     `json:"stock"`
}

func NewInventoryService(repo *repository.InventoryRepository, cfg *config.Config) *InventoryService {
	return &InventoryService{
		repo:                  repo,
		httpClient:            &http.Client{Timeout: 8 * time.Second},
		productServiceBaseURL: cfg.ProductServiceBaseURL,
		chaosMode:             cfg.ChaosMode,
		latencyProbability:    cfg.LatencyProbability,
		errorProbability:      cfg.ErrorProbability,
		chaosDelayMs:          cfg.ChaosDelayMs,
	}
}

func (s *InventoryService) Reserve(request *model.ReserveRequest, idempotencyKey string, correlationID string) (map[string]any, *model.ServiceError) {
	if err := validateReserveRequest(request); err != nil {
		return nil, err
	}

	idempotencyKey = strings.TrimSpace(idempotencyKey)
	if idempotencyKey == "" {
		return nil, &model.ServiceError{Code: "MISSING_IDEMPOTENCY_KEY", Message: "Idempotency-Key is required", HTTPStatus: http.StatusBadRequest}
	}

	response, svcErr := s.resolveExistingOperation(
		idempotencyKey,
		operationReserve,
		request.OrderID,
		request.ProductID,
		request.Quantity,
		correlationID,
		serviceStatusResv,
	)
	if response != nil || svcErr != nil {
		return response, svcErr
	}

	op := buildOperation(idempotencyKey, operationReserve, request.OrderID, request.ProductID, request.Quantity, correlationID)
	claimed, err := s.repo.ClaimOperation(op)
	if err != nil {
		return nil, dbServiceError(err)
	}
	if !claimed {
		return s.resolveClaimConflict(
			idempotencyKey,
			operationReserve,
			request.OrderID,
			request.ProductID,
			request.Quantity,
			correlationID,
			serviceStatusResv,
		)
	}

	if chaosErr := s.maybeInjectChaos(correlationID, "reserve"); chaosErr != nil {
		return nil, s.markOperationFailed(idempotencyKey, correlationID, chaosErr)
	}

	product, svcErr := s.getProduct(request.ProductID, correlationID)
	if svcErr != nil {
		return nil, s.markOperationFailed(idempotencyKey, correlationID, svcErr)
	}

	if product.Stock < request.Quantity {
		svcErr = &model.ServiceError{
			Code:       "OUT_OF_STOCK",
			Message:    "Not enough stock for requested product",
			HTTPStatus: httpConflictStatus,
		}
		return nil, s.markOperationFailed(idempotencyKey, correlationID, svcErr)
	}

	if svcErr = s.adjustStock("decrease-stock", request.ProductID, request.Quantity, correlationID); svcErr != nil {
		return nil, s.markOperationFailed(idempotencyKey, correlationID, svcErr)
	}

	if svcErr = s.markOperationSuccess(idempotencyKey, correlationID); svcErr != nil {
		return nil, svcErr
	}

	middleware.LogJSON("info", "inventory.reserve.success", map[string]any{
		"order_id":       request.OrderID,
		"product_id":     request.ProductID,
		"quantity":       request.Quantity,
		"correlation_id": correlationID,
	})

	return map[string]any{
		"status":             serviceStatusResv,
		"operationId":        op.ID,
		"idempotentReplay":   false,
		"correlationId":      correlationID,
		"availableStockHint": product.Stock - request.Quantity,
	}, nil
}

func (s *InventoryService) Release(request *model.ReleaseRequest, idempotencyKey string, correlationID string) (map[string]any, *model.ServiceError) {
	if err := validateReleaseRequest(request); err != nil {
		return nil, err
	}

	idempotencyKey = strings.TrimSpace(idempotencyKey)
	if idempotencyKey == "" {
		return nil, &model.ServiceError{Code: "MISSING_IDEMPOTENCY_KEY", Message: "Idempotency-Key is required", HTTPStatus: http.StatusBadRequest}
	}

	response, svcErr := s.resolveExistingOperation(
		idempotencyKey,
		operationRelease,
		request.OrderID,
		request.ProductID,
		request.Quantity,
		correlationID,
		serviceStatusRel,
	)
	if response != nil || svcErr != nil {
		return response, svcErr
	}

	op := buildOperation(idempotencyKey, operationRelease, request.OrderID, request.ProductID, request.Quantity, correlationID)
	claimed, err := s.repo.ClaimOperation(op)
	if err != nil {
		return nil, dbServiceError(err)
	}
	if !claimed {
		return s.resolveClaimConflict(
			idempotencyKey,
			operationRelease,
			request.OrderID,
			request.ProductID,
			request.Quantity,
			correlationID,
			serviceStatusRel,
		)
	}

	reserveOp, err := s.repo.FindByOrderAndType(request.OrderID, operationReserve)
	if err != nil {
		return nil, s.markOperationFailed(idempotencyKey, correlationID, dbServiceError(err))
	}
	if reserveOp == nil || reserveOp.Status != statusSuccess {
		return nil, s.markOperationFailed(idempotencyKey, correlationID, &model.ServiceError{
			Code:       "NO_RESERVED_STOCK",
			Message:    "No successful reserve operation found for this order",
			HTTPStatus: httpConflictStatus,
		})
	}
	if reserveOp.ProductID != request.ProductID || reserveOp.Quantity != request.Quantity {
		return nil, s.markOperationFailed(idempotencyKey, correlationID, &model.ServiceError{
			Code:       "RESERVATION_MISMATCH",
			Message:    "Release payload does not match successful reservation",
			HTTPStatus: httpConflictStatus,
		})
	}

	if chaosErr := s.maybeInjectChaos(correlationID, "release"); chaosErr != nil {
		return nil, s.markOperationFailed(idempotencyKey, correlationID, chaosErr)
	}

	if svcErr := s.adjustStock("increase-stock", request.ProductID, request.Quantity, correlationID); svcErr != nil {
		return nil, s.markOperationFailed(idempotencyKey, correlationID, svcErr)
	}

	if svcErr := s.markOperationSuccess(idempotencyKey, correlationID); svcErr != nil {
		return nil, svcErr
	}

	middleware.LogJSON("info", "inventory.release.success", map[string]any{
		"order_id":       request.OrderID,
		"product_id":     request.ProductID,
		"quantity":       request.Quantity,
		"correlation_id": correlationID,
	})

	return map[string]any{
		"status":           serviceStatusRel,
		"operationId":      op.ID,
		"idempotentReplay": false,
		"correlationId":    correlationID,
	}, nil
}

func (s *InventoryService) CheckAvailable(productID string, correlationID string) (*model.InventoryView, *model.ServiceError) {
	if strings.TrimSpace(productID) == "" {
		return nil, &model.ServiceError{Code: "INVALID_PRODUCT", Message: "productId is required", HTTPStatus: http.StatusBadRequest}
	}
	product, svcErr := s.getProduct(productID, correlationID)
	if svcErr != nil {
		return nil, svcErr
	}
	return &model.InventoryView{
		ProductID:      product.ID,
		AvailableStock: product.Stock,
		Source:         "product-service",
	}, nil
}

func (s *InventoryService) resolveExistingOperation(
	idempotencyKey string,
	operationType string,
	orderID string,
	productID string,
	quantity int,
	correlationID string,
	serviceStatus string,
) (map[string]any, *model.ServiceError) {
	existing, err := s.repo.FindByIdempotencyKey(idempotencyKey)
	if err != nil {
		return nil, dbServiceError(err)
	}
	if existing == nil {
		return nil, nil
	}
	return s.handleExistingOperation(existing, operationType, orderID, productID, quantity, correlationID, serviceStatus)
}

func (s *InventoryService) resolveClaimConflict(
	idempotencyKey string,
	operationType string,
	orderID string,
	productID string,
	quantity int,
	correlationID string,
	serviceStatus string,
) (map[string]any, *model.ServiceError) {
	byKey, err := s.repo.FindByIdempotencyKey(idempotencyKey)
	if err != nil {
		return nil, dbServiceError(err)
	}
	if byKey != nil {
		return s.handleExistingOperation(byKey, operationType, orderID, productID, quantity, correlationID, serviceStatus)
	}

	byOrder, err := s.repo.FindByOrderAndType(orderID, operationType)
	if err != nil {
		return nil, dbServiceError(err)
	}
	if byOrder == nil {
		return nil, &model.ServiceError{
			Code:       "IDEMPOTENCY_CONFLICT",
			Message:    "Conflicting request detected for this operation",
			HTTPStatus: httpConflictStatus,
		}
	}

	if byOrder.IdempotencyKey == idempotencyKey {
		return s.handleExistingOperation(byOrder, operationType, orderID, productID, quantity, correlationID, serviceStatus)
	}

	return nil, &model.ServiceError{
		Code:       "ORDER_OPERATION_CONFLICT",
		Message:    "Order already has an operation with a different idempotency key",
		HTTPStatus: httpConflictStatus,
	}
}

func (s *InventoryService) handleExistingOperation(
	existing *model.InventoryOperation,
	operationType string,
	orderID string,
	productID string,
	quantity int,
	correlationID string,
	serviceStatus string,
) (map[string]any, *model.ServiceError) {
	if !matchesOperation(existing, operationType, orderID, productID, quantity) {
		return nil, &model.ServiceError{
			Code:       "IDEMPOTENCY_CONFLICT",
			Message:    "Idempotency key already used with different payload",
			HTTPStatus: httpConflictStatus,
		}
	}

	switch strings.ToUpper(strings.TrimSpace(existing.Status)) {
	case statusSuccess:
		return map[string]any{
			"status":             serviceStatus,
			"operationId":        existing.ID,
			"idempotentReplay":   true,
			"correlationId":      correlationID,
			"inventoryOperation": existing,
		}, nil
	case statusInProgress:
		return nil, &model.ServiceError{
			Code:       "IDEMPOTENCY_IN_PROGRESS",
			Message:    "Operation is still in progress for this idempotency key",
			HTTPStatus: httpConflictStatus,
		}
	default:
		code := strings.TrimSpace(existing.ErrorCode)
		if code == "" {
			code = "IDEMPOTENCY_REPLAY_FAILED"
		}
		message := strings.TrimSpace(existing.ErrorMessage)
		if message == "" {
			message = "Previous request with this idempotency key failed"
		}
		return nil, &model.ServiceError{
			Code:       code,
			Message:    message,
			HTTPStatus: httpConflictStatus,
		}
	}
}

func (s *InventoryService) markOperationFailed(idempotencyKey string, correlationID string, original *model.ServiceError) *model.ServiceError {
	if err := s.repo.UpdateOperationResult(idempotencyKey, statusFailed, original.Code, original.Message, correlationID); err != nil {
		return dbServiceError(err)
	}
	return original
}

func (s *InventoryService) markOperationSuccess(idempotencyKey string, correlationID string) *model.ServiceError {
	if err := s.repo.UpdateOperationResult(idempotencyKey, statusSuccess, "", "", correlationID); err != nil {
		return dbServiceError(err)
	}
	return nil
}

func (s *InventoryService) maybeInjectChaos(correlationID string, stage string) *model.ServiceError {
	if !s.chaosMode {
		return nil
	}
	if s.latencyProbability > 0 && rand.Float64() < s.latencyProbability {
		delay := time.Duration(s.chaosDelayMs) * time.Millisecond
		if delay > 0 {
			time.Sleep(delay)
		}
	}
	if s.errorProbability > 0 && rand.Float64() < s.errorProbability {
		middleware.LogJSON("warn", "inventory.chaos.injected_error", map[string]any{
			"stage":          stage,
			"correlation_id": correlationID,
		})
		return &model.ServiceError{
			Code:       "CHAOS_FAILURE",
			Message:    "Injected chaos failure",
			HTTPStatus: http.StatusInternalServerError,
		}
	}
	return nil
}

func (s *InventoryService) getProduct(productID string, correlationID string) (*ProductView, *model.ServiceError) {
	endpoint := fmt.Sprintf("%s/products/%s", s.productServiceBaseURL, productID)
	req, _ := http.NewRequest(http.MethodGet, endpoint, nil)
	req.Header.Set("X-Correlation-Id", correlationID)

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return nil, &model.ServiceError{Code: "PRODUCT_SERVICE_UNAVAILABLE", Message: err.Error(), HTTPStatus: http.StatusBadGateway}
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNotFound {
		return nil, &model.ServiceError{Code: "INVALID_PRODUCT", Message: "Product not found", HTTPStatus: http.StatusNotFound}
	}
	if resp.StatusCode >= 400 {
		body, _ := io.ReadAll(resp.Body)
		return nil, &model.ServiceError{Code: "PRODUCT_SERVICE_ERROR", Message: string(body), HTTPStatus: http.StatusBadGateway}
	}

	var product ProductView
	if err := json.NewDecoder(resp.Body).Decode(&product); err != nil {
		return nil, &model.ServiceError{Code: "BAD_PRODUCT_RESPONSE", Message: err.Error(), HTTPStatus: http.StatusBadGateway}
	}
	if strings.TrimSpace(product.ID) == "" {
		product.ID = productID
	}
	return &product, nil
}

func (s *InventoryService) adjustStock(action string, productID string, quantity int, correlationID string) *model.ServiceError {
	endpoint := fmt.Sprintf("%s/products/%s/%s?quantity=%d", s.productServiceBaseURL, productID, action, quantity)
	parsed, err := url.Parse(endpoint)
	if err != nil {
		return &model.ServiceError{Code: "INVALID_ENDPOINT", Message: err.Error(), HTTPStatus: http.StatusInternalServerError}
	}

	req, _ := http.NewRequest(http.MethodPost, parsed.String(), nil)
	req.Header.Set("X-Internal-Caller", "inventory-service")
	req.Header.Set("X-Correlation-Id", correlationID)

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return &model.ServiceError{Code: "PRODUCT_SERVICE_UNAVAILABLE", Message: err.Error(), HTTPStatus: http.StatusBadGateway}
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		body, _ := io.ReadAll(resp.Body)
		errBody := strings.TrimSpace(string(body))
		if errBody == "" {
			errBody = "failed to adjust stock"
		}
		code := "PRODUCT_STOCK_UPDATE_FAILED"
		if resp.StatusCode == http.StatusNotFound {
			code = "INVALID_PRODUCT"
		}
		if resp.StatusCode == http.StatusBadRequest {
			code = "OUT_OF_STOCK"
		}
		return &model.ServiceError{Code: code, Message: errBody, HTTPStatus: httpConflictStatus}
	}

	return nil
}

func buildOperation(
	idempotencyKey string,
	operationType string,
	orderID string,
	productID string,
	quantity int,
	correlationID string,
) *model.InventoryOperation {
	return &model.InventoryOperation{
		ID:             newOperationID(),
		IdempotencyKey: idempotencyKey,
		OperationType:  operationType,
		OrderID:        orderID,
		ProductID:      productID,
		Quantity:       quantity,
		Status:         statusInProgress,
		CorrelationID:  correlationID,
		CreatedAt:      time.Now().UTC(),
	}
}

func matchesOperation(existing *model.InventoryOperation, operationType, orderID, productID string, quantity int) bool {
	if existing == nil {
		return false
	}
	return strings.EqualFold(existing.OperationType, operationType) &&
		existing.OrderID == orderID &&
		existing.ProductID == productID &&
		existing.Quantity == quantity
}

func dbServiceError(err error) *model.ServiceError {
	return &model.ServiceError{
		Code:       "DB_ERROR",
		Message:    err.Error(),
		HTTPStatus: http.StatusInternalServerError,
	}
}

func newOperationID() string {
	return strconv.FormatInt(time.Now().UTC().UnixNano(), 10)
}

func validateReserveRequest(request *model.ReserveRequest) *model.ServiceError {
	if request == nil {
		return &model.ServiceError{Code: "INVALID_REQUEST", Message: "request body is required", HTTPStatus: http.StatusBadRequest}
	}
	if strings.TrimSpace(request.OrderID) == "" {
		return &model.ServiceError{Code: "INVALID_REQUEST", Message: "orderId is required", HTTPStatus: http.StatusBadRequest}
	}
	if strings.TrimSpace(request.ProductID) == "" {
		return &model.ServiceError{Code: "INVALID_PRODUCT", Message: "productId is required", HTTPStatus: http.StatusBadRequest}
	}
	if request.Quantity <= 0 {
		return &model.ServiceError{Code: "INVALID_REQUEST", Message: "quantity must be greater than 0", HTTPStatus: http.StatusBadRequest}
	}
	return nil
}

func validateReleaseRequest(request *model.ReleaseRequest) *model.ServiceError {
	if request == nil {
		return &model.ServiceError{Code: "INVALID_REQUEST", Message: "request body is required", HTTPStatus: http.StatusBadRequest}
	}
	if strings.TrimSpace(request.OrderID) == "" {
		return &model.ServiceError{Code: "INVALID_REQUEST", Message: "orderId is required", HTTPStatus: http.StatusBadRequest}
	}
	if strings.TrimSpace(request.ProductID) == "" {
		return &model.ServiceError{Code: "INVALID_PRODUCT", Message: "productId is required", HTTPStatus: http.StatusBadRequest}
	}
	if request.Quantity <= 0 {
		return &model.ServiceError{Code: "INVALID_REQUEST", Message: "quantity must be greater than 0", HTTPStatus: http.StatusBadRequest}
	}
	return nil
}
