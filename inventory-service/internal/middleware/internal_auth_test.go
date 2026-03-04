package middleware

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestInternalServiceAuthRejectsCallerHeaderWithoutToken(t *testing.T) {
	handler := InternalServiceAuth("expected-token")(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	}))

	req := httptest.NewRequest(http.MethodPost, "/inventory/reserve", nil)
	req.Header.Set("X-Internal-Caller", "order-service")
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 when only caller header is present, got %d", rec.Code)
	}
}

func TestInternalServiceAuthAllowsXInternalToken(t *testing.T) {
	handler := InternalServiceAuth("expected-token")(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	}))

	req := httptest.NewRequest(http.MethodPost, "/inventory/reserve", nil)
	req.Header.Set("X-Internal-Token", "expected-token")
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusNoContent {
		t.Fatalf("expected 204 for valid X-Internal-Token, got %d", rec.Code)
	}
}

func TestInternalServiceAuthAllowsBearerToken(t *testing.T) {
	handler := InternalServiceAuth("expected-token")(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	}))

	req := httptest.NewRequest(http.MethodPost, "/inventory/release", nil)
	req.Header.Set("Authorization", "Bearer expected-token")
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusNoContent {
		t.Fatalf("expected 204 for valid bearer token, got %d", rec.Code)
	}
}

func TestInternalServiceAuthRejectsInvalidToken(t *testing.T) {
	handler := InternalServiceAuth("expected-token")(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	}))

	req := httptest.NewRequest(http.MethodPost, "/inventory/release", nil)
	req.Header.Set("X-Internal-Token", "wrong-token")
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusForbidden {
		t.Fatalf("expected 403 for invalid token, got %d", rec.Code)
	}
}
