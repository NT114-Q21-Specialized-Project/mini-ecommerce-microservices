package main

import (
	"context"
	"log"
	"net/http"
	"time"

	"github.com/gorilla/mux"

	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.21.0"

	"user-service/internal/config"
	"user-service/internal/handler"
	"user-service/internal/repository"
	"user-service/internal/service"
)

func initTracer() func() {
	ctx := context.Background()

	exporter, err := otlptracegrpc.New(ctx,
		otlptracegrpc.WithEndpoint("tempo:4317"),
		otlptracegrpc.WithInsecure(),
	)
	if err != nil {
		log.Fatalf("failed to init tracer: %v", err)
	}

	res := resource.NewWithAttributes(
		semconv.SchemaURL,
		semconv.ServiceName("user-service"),
	)

	tp := sdktrace.NewTracerProvider(
		sdktrace.WithBatcher(exporter),
		sdktrace.WithResource(res),
	)

	otel.SetTracerProvider(tp)

	return func() {
		_ = tp.Shutdown(ctx)
	}
}

func main() {
	shutdown := initTracer()
	defer shutdown()

	var cfg *config.Config
	var err error

	// =========================
	// WAIT FOR DB (RETRY)
	// =========================
	for i := 1; i <= 10; i++ {
		cfg, err = config.LoadConfig()
		if err == nil {
			log.Println("Connected to database")
			break
		}

		log.Printf("Database not ready (attempt %d/10): %v\n", i, err)
		time.Sleep(2 * time.Second)
	}

	if err != nil {
		log.Fatalf("Cannot connect to database after retries: %v", err)
	}

	// =========================
	// INIT DEPENDENCIES
	// =========================
	repo := repository.NewUserRepository(cfg.DB)
	svc := service.NewUserService(repo)
	h := handler.NewUserHandler(svc)

	// =========================
	// ROUTES
	// =========================
	r := mux.NewRouter()

	// =========================
	// SYSTEM / HEALTH
	// =========================
	r.HandleFunc("/health", h.Health).Methods("GET")

	// =========================
	// AUTH
	// =========================
	r.HandleFunc("/users/login", h.Login).Methods("POST")

	// =========================
	// PUBLIC USER APIs
	// =========================
	r.HandleFunc("/users", h.GetUsers).Methods("GET")
	r.HandleFunc("/users", h.CreateUser).Methods("POST")

	r.HandleFunc("/users/by-email", h.GetUserByEmail).Methods("GET")
	r.HandleFunc("/users/email-exists", h.EmailExists).Methods("GET")

	r.HandleFunc("/users/{id}", h.GetUserByID).Methods("GET")
	r.HandleFunc("/users/{id}", h.UpdateUser).Methods("PUT")
	r.HandleFunc("/users/{id}", h.DeleteUser).Methods("DELETE")

	r.HandleFunc("/users/{id}/activate", h.ActivateUser).Methods("PATCH")
	r.HandleFunc("/users/{id}/deactivate", h.DeactivateUser).Methods("PATCH")

	r.HandleFunc("/users/stats", h.UserStats).Methods("GET")

	// =========================
	// INTERNAL APIs (SERVICE-TO-SERVICE)
	// =========================
	r.HandleFunc("/users/{id}/exists", h.UserExists).Methods("GET")
	r.HandleFunc("/users/{id}/role", h.GetUserRole).Methods("GET")

	r.HandleFunc("/internal/users/{id}/validate", h.ValidateUser).Methods("GET")

	log.Println("User Service running on :8080")
	log.Fatal(http.ListenAndServe(
		":8080",
		otelhttp.NewHandler(r, "user-service"),
	))
}
