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
	if err := validateRequest(request); err != nil {
		return nil, err
	}
	if strings.TrimSpace(idempotencyKey) == "" {
		return nil, &model.ServiceError{Code: "MISSING_IDEMPOTENCY_KEY", Message: "Idempotency-Key is required", HTTPStatus: http.StatusBadRequest}
	}

	existing, err := s.repo.FindByIdempotencyKey(idempotencyKey)
	if err != nil {
		return nil, &model.ServiceError{Code: "DB_ERROR", Message: err.Error(), HTTPStatus: http.StatusInternalServerError}
	}
	if existing != nil {
		if existing.Status == "SUCCESS" {
			return map[string]any{
				"status":            "RESERVED",
				"operationId":       existing.ID,
				"idempotentReplay":  true,
				"correlationId":     correlationID,
				"inventoryOperation": existing,
			}, nil
		}
		return nil, &model.ServiceError{Code: existing.ErrorCode, Message: existing.ErrorMessage, HTTPStatus: http.StatusConflict}
	}

	if chaosErr := s.maybeInjectChaos(correlationID, "reserve"); chaosErr != nil {
		s.recordFailedOperation(idempotencyKey, "RESERVE", request.OrderID, request.ProductID, request.Quantity, correlationID, chaosErr)
		return nil, chaosErr
	}

	product, svcErr := s.getProduct(request.ProductID, correlationID)
	if svcErr != nil {
		s.recordFailedOperation(idempotencyKey, "RESERVE", request.OrderID, request.ProductID, request.Quantity, correlationID, svcErr)
		return nil, svcErr
	}
	if product.Stock < request.Quantity {
		svcErr = &model.ServiceError{Code: "OUT_OF_STOCK", Message: "Not enough stock for requested product", HTTPStatus: http.StatusConflict}
		s.recordFailedOperation(idempotencyKey, "RESERVE", request.OrderID, request.ProductID, request.Quantity, correlationID, svcErr)
		return nil, svcErr
	}

	if svcErr = s.adjustStock("decrease-stock", request.ProductID, request.Quantity, correlationID); svcErr != nil {
		s.recordFailedOperation(idempotencyKey, "RESERVE", request.OrderID, request.ProductID, request.Quantity, correlationID, svcErr)
		return nil, svcErr
	}

	op := &model.InventoryOperation{
		ID:             newOperationID(),
		IdempotencyKey: idempotencyKey,
		OperationType:  "RESERVE",
		OrderID:        request.OrderID,
		ProductID:      request.ProductID,
		Quantity:       request.Quantity,
		Status:         "SUCCESS",
		CorrelationID:  correlationID,
		CreatedAt:      time.Now().UTC(),
	}
	if err := s.repo.CreateOperation(op); err != nil {
		return nil, &model.ServiceError{Code: "DB_ERROR", Message: err.Error(), HTTPStatus: http.StatusInternalServerError}
	}

	middleware.LogJSON("info", "inventory.reserve.success", map[string]any{
		"order_id":       request.OrderID,
		"product_id":     request.ProductID,
		"quantity":       request.Quantity,
		"correlation_id": correlationID,
	})

	return map[string]any{
		"status":            "RESERVED",
		"operationId":       op.ID,
		"idempotentReplay":  false,
		"correlationId":     correlationID,
		"availableStockHint": product.Stock - request.Quantity,
	}, nil
}

func (s *InventoryService) Release(request *model.ReleaseRequest, idempotencyKey string, correlationID string) (map[string]any, *model.ServiceError) {
	if err := validateReleaseRequest(request); err != nil {
		return nil, err
	}
	if strings.TrimSpace(idempotencyKey) == "" {
		return nil, &model.ServiceError{Code: "MISSING_IDEMPOTENCY_KEY", Message: "Idempotency-Key is required", HTTPStatus: http.StatusBadRequest}
	}

	existing, err := s.repo.FindByIdempotencyKey(idempotencyKey)
	if err != nil {
		return nil, &model.ServiceError{Code: "DB_ERROR", Message: err.Error(), HTTPStatus: http.StatusInternalServerError}
	}
	if existing != nil {
		if existing.Status == "SUCCESS" {
			return map[string]any{
				"status":           "RELEASED",
				"operationId":      existing.ID,
				"idempotentReplay": true,
				"correlationId":    correlationID,
			}, nil
		}
		return nil, &model.ServiceError{Code: existing.ErrorCode, Message: existing.ErrorMessage, HTTPStatus: http.StatusConflict}
	}

	if chaosErr := s.maybeInjectChaos(correlationID, "release"); chaosErr != nil {
		s.recordFailedOperation(idempotencyKey, "RELEASE", request.OrderID, request.ProductID, request.Quantity, correlationID, chaosErr)
		return nil, chaosErr
	}

	if svcErr := s.adjustStock("increase-stock", request.ProductID, request.Quantity, correlationID); svcErr != nil {
		s.recordFailedOperation(idempotencyKey, "RELEASE", request.OrderID, request.ProductID, request.Quantity, correlationID, svcErr)
		return nil, svcErr
	}

	op := &model.InventoryOperation{
		ID:             newOperationID(),
		IdempotencyKey: idempotencyKey,
		OperationType:  "RELEASE",
		OrderID:        request.OrderID,
		ProductID:      request.ProductID,
		Quantity:       request.Quantity,
		Status:         "SUCCESS",
		CorrelationID:  correlationID,
		CreatedAt:      time.Now().UTC(),
	}
	if err := s.repo.CreateOperation(op); err != nil {
		return nil, &model.ServiceError{Code: "DB_ERROR", Message: err.Error(), HTTPStatus: http.StatusInternalServerError}
	}

	middleware.LogJSON("info", "inventory.release.success", map[string]any{
		"order_id":       request.OrderID,
		"product_id":     request.ProductID,
		"quantity":       request.Quantity,
		"correlation_id": correlationID,
	})

	return map[string]any{
		"status":           "RELEASED",
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
		return &model.ServiceError{Code: code, Message: errBody, HTTPStatus: http.StatusConflict}
	}

	return nil
}

func (s *InventoryService) recordFailedOperation(idempotencyKey, operationType, orderID, productID string, quantity int, correlationID string, svcErr *model.ServiceError) {
	op := &model.InventoryOperation{
		ID:             newOperationID(),
		IdempotencyKey: idempotencyKey,
		OperationType:  operationType,
		OrderID:        orderID,
		ProductID:      productID,
		Quantity:       quantity,
		Status:         "FAILED",
		ErrorCode:      svcErr.Code,
		ErrorMessage:   svcErr.Message,
		CorrelationID:  correlationID,
		CreatedAt:      time.Now().UTC(),
	}
	_ = s.repo.CreateOperation(op)
}

func newOperationID() string {
	return strconv.FormatInt(time.Now().UTC().UnixNano(), 10)
}

func validateRequest(request *model.ReserveRequest) *model.ServiceError {
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
