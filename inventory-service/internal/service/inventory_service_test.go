package service

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"

	"inventory-service/internal/model"

	"github.com/google/uuid"
	"github.com/lib/pq"
)

func TestReserveConcurrentSameIdempotencyKeySingleStockMutation(t *testing.T) {
	repo := newFakeInventoryStore()
	product := newFakeProductTransport(t, "p-1", 20, "test-internal-token")
	product.setDelay("decrease-stock", 150*time.Millisecond)

	svc := newTestInventoryService(repo, "http://product-service", "test-internal-token", product)
	request := &model.ReserveRequest{
		OrderID:   "order-1",
		ProductID: "p-1",
		Quantity:  2,
	}

	const workers = 20
	var wg sync.WaitGroup
	wg.Add(workers)

	type outcome struct {
		replay bool
		code   string
	}
	results := make(chan outcome, workers)
	start := make(chan struct{})

	for i := 0; i < workers; i++ {
		go func(idx int) {
			defer wg.Done()
			<-start
			resp, err := svc.Reserve(request, "reserve-key-1", fmt.Sprintf("corr-%d", idx))
			if err != nil {
				results <- outcome{code: err.Code}
				return
			}
			replay, _ := resp["idempotentReplay"].(bool)
			results <- outcome{replay: replay}
		}(i)
	}
	close(start)

	wg.Wait()
	close(results)

	nonReplaySuccess := 0
	replaySuccess := 0
	for item := range results {
		switch {
		case item.code == "":
			if item.replay {
				replaySuccess++
			} else {
				nonReplaySuccess++
			}
		default:
			t.Fatalf("unexpected reserve outcome code=%s", item.code)
		}
	}

	if nonReplaySuccess != 1 {
		t.Fatalf("expected exactly 1 non-replay success, got %d", nonReplaySuccess)
	}
	if replaySuccess != workers-1 {
		t.Fatalf("expected %d replay responses, got %d", workers-1, replaySuccess)
	}

	finalReplay, svcErr := svc.Reserve(request, "reserve-key-1", "corr-final")
	if svcErr != nil {
		t.Fatalf("expected replay to succeed, got error: %+v", svcErr)
	}
	if replay, _ := finalReplay["idempotentReplay"].(bool); !replay {
		t.Fatalf("expected final reserve replay to return idempotentReplay=true")
	}

	if got := product.stock(); got != 18 {
		t.Fatalf("expected stock to decrease once to 18, got %d", got)
	}
}

func TestReleaseConcurrentSameIdempotencyKeySingleStockMutation(t *testing.T) {
	repo := newFakeInventoryStore()
	product := newFakeProductTransport(t, "p-2", 10, "test-internal-token")
	product.setDelay("increase-stock", 150*time.Millisecond)

	svc := newTestInventoryService(repo, "http://product-service", "test-internal-token", product)

	reserveRequest := &model.ReserveRequest{
		OrderID:   "order-2",
		ProductID: "p-2",
		Quantity:  3,
	}
	if _, err := svc.Reserve(reserveRequest, "reserve-key-2", "corr-reserve"); err != nil {
		t.Fatalf("reserve setup failed: %+v", err)
	}

	releaseRequest := &model.ReleaseRequest{
		OrderID:   "order-2",
		ProductID: "p-2",
		Quantity:  3,
	}

	const workers = 20
	var wg sync.WaitGroup
	wg.Add(workers)

	type outcome struct {
		replay bool
		code   string
	}
	results := make(chan outcome, workers)
	start := make(chan struct{})

	for i := 0; i < workers; i++ {
		go func(idx int) {
			defer wg.Done()
			<-start
			resp, err := svc.Release(releaseRequest, "release-key-2", fmt.Sprintf("corr-release-%d", idx))
			if err != nil {
				results <- outcome{code: err.Code}
				return
			}
			replay, _ := resp["idempotentReplay"].(bool)
			results <- outcome{replay: replay}
		}(i)
	}
	close(start)

	wg.Wait()
	close(results)

	nonReplaySuccess := 0
	replaySuccess := 0
	for item := range results {
		switch {
		case item.code == "":
			if item.replay {
				replaySuccess++
			} else {
				nonReplaySuccess++
			}
		default:
			t.Fatalf("unexpected release outcome code=%s", item.code)
		}
	}

	if nonReplaySuccess != 1 {
		t.Fatalf("expected exactly 1 non-replay success, got %d", nonReplaySuccess)
	}
	if replaySuccess != workers-1 {
		t.Fatalf("expected %d replay responses, got %d", workers-1, replaySuccess)
	}

	finalReplay, svcErr := svc.Release(releaseRequest, "release-key-2", "corr-final")
	if svcErr != nil {
		t.Fatalf("expected replay to succeed, got error: %+v", svcErr)
	}
	if replay, _ := finalReplay["idempotentReplay"].(bool); !replay {
		t.Fatalf("expected final release replay to return idempotentReplay=true")
	}

	if got := product.stock(); got != 10 {
		t.Fatalf("expected stock to increase once back to 10, got %d", got)
	}
}

func TestReserveOperationIDIsUUID(t *testing.T) {
	repo := newFakeInventoryStore()
	product := newFakeProductTransport(t, "p-uuid-reserve", 8, "test-internal-token")
	svc := newTestInventoryService(repo, "http://product-service", "test-internal-token", product)

	resp, svcErr := svc.Reserve(&model.ReserveRequest{
		OrderID:   "order-uuid-reserve",
		ProductID: "p-uuid-reserve",
		Quantity:  1,
	}, "reserve-key-uuid", "corr-reserve-uuid")
	if svcErr != nil {
		t.Fatalf("reserve failed: %+v", svcErr)
	}

	operationID, _ := resp["operationId"].(string)
	if _, err := uuid.Parse(operationID); err != nil {
		t.Fatalf("expected UUID operationId, got %q: %v", operationID, err)
	}
}

func TestReleaseOperationIDIsUUID(t *testing.T) {
	repo := newFakeInventoryStore()
	product := newFakeProductTransport(t, "p-uuid-release", 8, "test-internal-token")
	svc := newTestInventoryService(repo, "http://product-service", "test-internal-token", product)

	if _, svcErr := svc.Reserve(&model.ReserveRequest{
		OrderID:   "order-uuid-release",
		ProductID: "p-uuid-release",
		Quantity:  2,
	}, "reserve-key-uuid-release", "corr-reserve-uuid-release"); svcErr != nil {
		t.Fatalf("reserve setup failed: %+v", svcErr)
	}

	resp, svcErr := svc.Release(&model.ReleaseRequest{
		OrderID:   "order-uuid-release",
		ProductID: "p-uuid-release",
		Quantity:  2,
	}, "release-key-uuid-release", "corr-release-uuid-release")
	if svcErr != nil {
		t.Fatalf("release failed: %+v", svcErr)
	}

	operationID, _ := resp["operationId"].(string)
	if _, err := uuid.Parse(operationID); err != nil {
		t.Fatalf("expected UUID operationId, got %q: %v", operationID, err)
	}
}

func TestMapDownstreamStatusNormalized(t *testing.T) {
	tests := []struct {
		name        string
		statusCode  int
		body        string
		expectCode  string
		expectHTTP  int
		expectMatch string
	}{
		{
			name:        "maps 404 to invalid product",
			statusCode:  http.StatusNotFound,
			body:        `{"error":{"message":"product not found"}}`,
			expectCode:  "INVALID_PRODUCT",
			expectHTTP:  http.StatusNotFound,
			expectMatch: "product not found",
		},
		{
			name:        "maps 409 to out of stock",
			statusCode:  http.StatusConflict,
			body:        `{"code":"INSUFFICIENT_STOCK","message":"not enough stock"}`,
			expectCode:  "OUT_OF_STOCK",
			expectHTTP:  http.StatusConflict,
			expectMatch: "not enough stock",
		},
		{
			name:        "maps 5xx to service unavailable",
			statusCode:  http.StatusInternalServerError,
			body:        `{"message":"backend failed"}`,
			expectCode:  "PRODUCT_SERVICE_UNAVAILABLE",
			expectHTTP:  http.StatusServiceUnavailable,
			expectMatch: "backend failed",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			serviceErr := mapDownstreamStatus(tt.statusCode, tt.body)
			if serviceErr.Code != tt.expectCode {
				t.Fatalf("expected code %s, got %s", tt.expectCode, serviceErr.Code)
			}
			if serviceErr.HTTPStatus != tt.expectHTTP {
				t.Fatalf("expected HTTP status %d, got %d", tt.expectHTTP, serviceErr.HTTPStatus)
			}
			if !strings.Contains(strings.ToLower(serviceErr.Message), strings.ToLower(tt.expectMatch)) {
				t.Fatalf("expected message to contain %q, got %q", tt.expectMatch, serviceErr.Message)
			}
		})
	}
}

func newTestInventoryService(repo inventoryStore, productBaseURL string, internalToken string, transport http.RoundTripper) *InventoryService {
	client := &http.Client{Timeout: 2 * time.Second}
	if transport != nil {
		client.Transport = transport
	}
	return &InventoryService{
		repo:                   repo,
		httpClient:             client,
		productServiceBaseURL:  strings.TrimRight(productBaseURL, "/"),
		internalServiceToken:   internalToken,
		idempotencyWaitTimeout: 3 * time.Second,
		idempotencyPollWindow:  10 * time.Millisecond,
	}
}

type fakeInventoryStore struct {
	mu      sync.Mutex
	byKey   map[string]*model.InventoryOperation
	byOrder map[string]string
}

func newFakeInventoryStore() *fakeInventoryStore {
	return &fakeInventoryStore{
		byKey:   make(map[string]*model.InventoryOperation),
		byOrder: make(map[string]string),
	}
}

func (f *fakeInventoryStore) ClaimOrGetByIdempotency(operation *model.InventoryOperation) (*model.InventoryOperation, bool, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	if existing, ok := f.byKey[operation.IdempotencyKey]; ok {
		return cloneOperation(existing), false, nil
	}

	orderKey := operation.OrderID + "|" + strings.ToUpper(operation.OperationType)
	if existingKey, ok := f.byOrder[orderKey]; ok && existingKey != operation.IdempotencyKey {
		return nil, false, &pq.Error{Code: "23505"}
	}

	stored := cloneOperation(operation)
	f.byKey[stored.IdempotencyKey] = stored
	if operation.OrderID != "" {
		f.byOrder[orderKey] = stored.IdempotencyKey
	}
	return cloneOperation(stored), true, nil
}

func (f *fakeInventoryStore) FindByOrderAndType(orderID string, operationType string) (*model.InventoryOperation, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	key := orderID + "|" + strings.ToUpper(operationType)
	idempotencyKey, ok := f.byOrder[key]
	if !ok {
		return nil, nil
	}
	existing := f.byKey[idempotencyKey]
	if existing == nil {
		return nil, nil
	}
	return cloneOperation(existing), nil
}

func (f *fakeInventoryStore) FindByIdempotencyKey(idempotencyKey string) (*model.InventoryOperation, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	existing := f.byKey[idempotencyKey]
	if existing == nil {
		return nil, nil
	}
	return cloneOperation(existing), nil
}

func (f *fakeInventoryStore) UpdateOperationResult(idempotencyKey string, status string, errorCode string, errorMessage string, correlationID string) error {
	f.mu.Lock()
	defer f.mu.Unlock()

	existing := f.byKey[idempotencyKey]
	if existing == nil {
		return fmt.Errorf("missing operation for idempotency key %s", idempotencyKey)
	}
	existing.Status = status
	existing.ErrorCode = errorCode
	existing.ErrorMessage = errorMessage
	existing.CorrelationID = correlationID
	return nil
}

func cloneOperation(source *model.InventoryOperation) *model.InventoryOperation {
	if source == nil {
		return nil
	}
	cp := *source
	return &cp
}

type fakeProductTransport struct {
	t             *testing.T
	productID     string
	internalToken string
	mu            sync.Mutex
	currentStock  int
	delayByAction map[string]time.Duration
}

func newFakeProductTransport(t *testing.T, productID string, stock int, internalToken string) *fakeProductTransport {
	t.Helper()
	return &fakeProductTransport{
		t:             t,
		productID:     productID,
		internalToken: internalToken,
		currentStock:  stock,
		delayByAction: make(map[string]time.Duration),
	}
}

func (f *fakeProductTransport) stock() int {
	f.mu.Lock()
	defer f.mu.Unlock()
	return f.currentStock
}

func (f *fakeProductTransport) checkInternalHeaders(r *http.Request) bool {
	return r.Header.Get("X-Internal-Token") == f.internalToken
}

func (f *fakeProductTransport) setDelay(action string, delay time.Duration) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.delayByAction[strings.TrimSpace(action)] = delay
}

func (f *fakeProductTransport) delayFor(action string) time.Duration {
	f.mu.Lock()
	defer f.mu.Unlock()
	return f.delayByAction[strings.TrimSpace(action)]
}

func (f *fakeProductTransport) RoundTrip(r *http.Request) (*http.Response, error) {
	switch {
	case r.Method == http.MethodGet && r.URL.Path == "/products/"+f.productID:
		f.mu.Lock()
		stock := f.currentStock
		f.mu.Unlock()
		payload := map[string]any{
			"id":    f.productID,
			"name":  "Test Product",
			"price": 100.0,
			"stock": stock,
		}
		return jsonResponse(http.StatusOK, payload), nil

	case r.Method == http.MethodPost && r.URL.Path == "/products/"+f.productID+"/decrease-stock":
		if !f.checkInternalHeaders(r) {
			return jsonResponse(http.StatusForbidden, map[string]any{"code": "FORBIDDEN_INTERNAL_ENDPOINT", "message": "Forbidden internal endpoint"}), nil
		}
		if delay := f.delayFor("decrease-stock"); delay > 0 {
			time.Sleep(delay)
		}
		quantity, err := strconv.Atoi(r.URL.Query().Get("quantity"))
		if err != nil || quantity <= 0 {
			return jsonResponse(http.StatusBadRequest, map[string]any{"code": "INVALID_QUANTITY", "message": "invalid quantity"}), nil
		}
		f.mu.Lock()
		defer f.mu.Unlock()
		if f.currentStock < quantity {
			return jsonResponse(http.StatusBadRequest, map[string]any{"code": "INSUFFICIENT_STOCK", "message": "insufficient stock"}), nil
		}
		f.currentStock -= quantity
		return emptyResponse(http.StatusOK), nil

	case r.Method == http.MethodPost && r.URL.Path == "/products/"+f.productID+"/increase-stock":
		if !f.checkInternalHeaders(r) {
			return jsonResponse(http.StatusForbidden, map[string]any{"code": "FORBIDDEN_INTERNAL_ENDPOINT", "message": "Forbidden internal endpoint"}), nil
		}
		if delay := f.delayFor("increase-stock"); delay > 0 {
			time.Sleep(delay)
		}
		quantity, err := strconv.Atoi(r.URL.Query().Get("quantity"))
		if err != nil || quantity <= 0 {
			return jsonResponse(http.StatusBadRequest, map[string]any{"code": "INVALID_QUANTITY", "message": "invalid quantity"}), nil
		}
		f.mu.Lock()
		f.currentStock += quantity
		f.mu.Unlock()
		return emptyResponse(http.StatusOK), nil

	default:
		return jsonResponse(http.StatusNotFound, map[string]any{"code": "NOT_FOUND", "message": "not found"}), nil
	}
}

func jsonResponse(status int, payload any) *http.Response {
	body, _ := json.Marshal(payload)
	return &http.Response{
		StatusCode: status,
		Header:     http.Header{"Content-Type": []string{"application/json"}},
		Body:       io.NopCloser(strings.NewReader(string(body))),
	}
}

func emptyResponse(status int) *http.Response {
	return &http.Response{
		StatusCode: status,
		Header:     http.Header{},
		Body:       io.NopCloser(strings.NewReader("")),
	}
}
