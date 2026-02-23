package middleware

import (
	"context"
	"encoding/json"
	"log"
	"math/rand"
	"net/http"
	"strings"
	"time"
)

type contextKey string

const correlationIDKey contextKey = "correlationId"

func init() {
	rand.Seed(time.Now().UnixNano())
}

func randomID() string {
	const alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
	builder := strings.Builder{}
	for i := 0; i < 16; i++ {
		builder.WriteByte(alphabet[rand.Intn(len(alphabet))])
	}
	return builder.String()
}

func Correlation(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		correlationID := strings.TrimSpace(r.Header.Get("X-Correlation-Id"))
		if correlationID == "" {
			correlationID = randomID()
		}
		ctx := context.WithValue(r.Context(), correlationIDKey, correlationID)
		w.Header().Set("X-Correlation-Id", correlationID)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

func CorrelationIDFromContext(ctx context.Context) string {
	value, _ := ctx.Value(correlationIDKey).(string)
	if strings.TrimSpace(value) == "" {
		return randomID()
	}
	return value
}

func LogJSON(level string, msg string, fields map[string]any) {
	payload := map[string]any{
		"ts":    time.Now().UTC().Format(time.RFC3339Nano),
		"level": level,
		"msg":   msg,
	}
	for key, value := range fields {
		payload[key] = value
	}
	data, err := json.Marshal(payload)
	if err != nil {
		log.Printf("{\"level\":\"error\",\"msg\":\"marshal log failed\",\"error\":%q}", err.Error())
		return
	}
	log.Print(string(data))
}

func RequestLogger(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		next.ServeHTTP(w, r)
		LogJSON("info", "request.completed", map[string]any{
			"method":        r.Method,
			"path":          r.URL.Path,
			"correlation_id": CorrelationIDFromContext(r.Context()),
			"duration_ms":   time.Since(start).Milliseconds(),
		})
	})
}
