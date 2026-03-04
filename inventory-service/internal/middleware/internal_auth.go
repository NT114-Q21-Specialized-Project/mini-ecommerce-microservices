package middleware

import (
	"crypto/subtle"
	"encoding/json"
	"net/http"
	"strings"
)

func InternalServiceAuth(expectedToken string) func(http.Handler) http.Handler {
	normalized := strings.TrimSpace(expectedToken)

	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			correlationID := CorrelationIDFromContext(r.Context())

			if normalized == "" {
				writeInternalAuthError(w, correlationID, http.StatusInternalServerError, "INTERNAL_AUTH_MISCONFIG", "Internal authentication is not configured")
				return
			}

			providedToken := extractInternalToken(r)
			if providedToken == "" {
				writeInternalAuthError(w, correlationID, http.StatusUnauthorized, "INTERNAL_AUTH_REQUIRED", "Missing internal authentication token")
				return
			}

			if !secureTokenMatch(providedToken, normalized) {
				writeInternalAuthError(w, correlationID, http.StatusForbidden, "INTERNAL_AUTH_FAILED", "Invalid internal authentication token")
				return
			}

			next.ServeHTTP(w, r)
		})
	}
}

func extractInternalToken(r *http.Request) string {
	token := strings.TrimSpace(r.Header.Get("X-Internal-Token"))
	if token != "" {
		return token
	}

	authorization := strings.TrimSpace(r.Header.Get("Authorization"))
	if authorization == "" {
		return ""
	}

	const bearerPrefix = "Bearer "
	if len(authorization) < len(bearerPrefix) || !strings.EqualFold(authorization[:len(bearerPrefix)], bearerPrefix) {
		return ""
	}
	return strings.TrimSpace(authorization[len(bearerPrefix):])
}

func secureTokenMatch(provided string, expected string) bool {
	if len(provided) == 0 || len(expected) == 0 {
		return false
	}
	if len(provided) != len(expected) {
		return false
	}
	return subtle.ConstantTimeCompare([]byte(provided), []byte(expected)) == 1
}

func writeInternalAuthError(w http.ResponseWriter, correlationID string, status int, code string, message string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(map[string]any{
		"error": map[string]any{
			"code":    code,
			"message": message,
		},
		"correlationId": correlationID,
	})
}
