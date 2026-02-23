package handler

import (
	"encoding/json"
	"net/http"
	"strconv"
	"strings"
	"time"

	"inventory-service/internal/middleware"
	"inventory-service/internal/model"
	"inventory-service/internal/service"

	"github.com/gorilla/mux"
)

type InventoryHandler struct {
	service *service.InventoryService
}

func NewInventoryHandler(service *service.InventoryService) *InventoryHandler {
	return &InventoryHandler{service: service}
}

func (h *InventoryHandler) Health(w http.ResponseWriter, _ *http.Request) {
	respondJSON(w, http.StatusOK, map[string]any{"status": "ok"})
}

func (h *InventoryHandler) Reserve(w http.ResponseWriter, r *http.Request) {
	correlationID := middleware.CorrelationIDFromContext(r.Context())
	var request model.ReserveRequest
	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		writeError(w, correlationID, &model.ServiceError{Code: "INVALID_JSON", Message: "invalid request body", HTTPStatus: http.StatusBadRequest})
		return
	}

	idempotencyKey := strings.TrimSpace(r.Header.Get("Idempotency-Key"))
	if idempotencyKey == "" {
		idempotencyKey = strings.TrimSpace(request.IdempotencyKey)
	}

	response, svcErr := h.service.Reserve(&request, idempotencyKey, correlationID)
	if svcErr != nil {
		writeError(w, correlationID, svcErr)
		return
	}
	respondJSON(w, http.StatusOK, response)
}

func (h *InventoryHandler) Release(w http.ResponseWriter, r *http.Request) {
	correlationID := middleware.CorrelationIDFromContext(r.Context())
	var request model.ReleaseRequest
	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		writeError(w, correlationID, &model.ServiceError{Code: "INVALID_JSON", Message: "invalid request body", HTTPStatus: http.StatusBadRequest})
		return
	}

	idempotencyKey := strings.TrimSpace(r.Header.Get("Idempotency-Key"))
	if idempotencyKey == "" {
		idempotencyKey = strings.TrimSpace(request.IdempotencyKey)
	}

	response, svcErr := h.service.Release(&request, idempotencyKey, correlationID)
	if svcErr != nil {
		writeError(w, correlationID, svcErr)
		return
	}
	respondJSON(w, http.StatusOK, response)
}

func (h *InventoryHandler) GetInventory(w http.ResponseWriter, r *http.Request) {
	correlationID := middleware.CorrelationIDFromContext(r.Context())
	productID := mux.Vars(r)["productId"]
	view, svcErr := h.service.CheckAvailable(productID, correlationID)
	if svcErr != nil {
		writeError(w, correlationID, svcErr)
		return
	}
	respondJSON(w, http.StatusOK, view)
}

func (h *InventoryHandler) SimulateCPU(w http.ResponseWriter, r *http.Request) {
	seconds := parseQueryInt(r, "seconds", 2)
	if seconds > 20 {
		seconds = 20
	}

	end := time.Now().Add(time.Duration(seconds) * time.Second)
	for time.Now().Before(end) {
		_ = 7 * 7
	}

	respondJSON(w, http.StatusOK, map[string]any{
		"status":  "ok",
		"seconds": seconds,
	})
}

func (h *InventoryHandler) SimulateMemory(w http.ResponseWriter, r *http.Request) {
	mb := parseQueryInt(r, "mb", 64)
	if mb > 512 {
		mb = 512
	}
	buffer := make([]byte, mb*1024*1024)
	for i := 0; i < len(buffer); i += 4096 {
		buffer[i] = byte(i % 255)
	}
	time.Sleep(2 * time.Second)
	_ = buffer

	respondJSON(w, http.StatusOK, map[string]any{
		"status": "ok",
		"mb":     mb,
	})
}

func parseQueryInt(r *http.Request, key string, fallback int) int {
	value := strings.TrimSpace(r.URL.Query().Get(key))
	if value == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func writeError(w http.ResponseWriter, correlationID string, svcErr *model.ServiceError) {
	respondJSON(w, svcErr.HTTPStatus, map[string]any{
		"error": map[string]any{
			"code":    svcErr.Code,
			"message": svcErr.Message,
		},
		"correlationId": correlationID,
	})
}

func respondJSON(w http.ResponseWriter, status int, payload any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(payload)
}
