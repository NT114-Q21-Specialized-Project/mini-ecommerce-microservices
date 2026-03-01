package handler

import (
	"encoding/json"
	"net/http"
	"strings"

	"github.com/google/uuid"
)

type errorPayload struct {
	Error struct {
		Code    string `json:"code"`
		Message string `json:"message"`
	} `json:"error"`
	CorrelationID string `json:"correlationId"`
}

func writeJSON(w http.ResponseWriter, status int, payload any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(payload)
}

func writeError(w http.ResponseWriter, r *http.Request, status int, code, message string) {
	correlationID := requestCorrelationID(r)

	body := errorPayload{CorrelationID: correlationID}
	body.Error.Code = code
	body.Error.Message = message

	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("X-Correlation-ID", correlationID)
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}

func requestCorrelationID(r *http.Request) string {
	if r == nil {
		return uuid.NewString()
	}

	correlationID := strings.TrimSpace(r.Header.Get("X-Correlation-ID"))
	if correlationID == "" {
		return uuid.NewString()
	}

	return correlationID
}
