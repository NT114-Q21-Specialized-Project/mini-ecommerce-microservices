package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
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
	"user-service/internal/migration"
	"user-service/internal/repository"
	"user-service/internal/service"
)

func getEnv(key, fallback string) string {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	return value
}

func normalizeOTLPEndpoint(raw string) string {
	value := strings.TrimSpace(raw)
	value = strings.TrimPrefix(value, "http://")
	value = strings.TrimPrefix(value, "https://")
	return strings.TrimSuffix(value, "/")
}

func getEnvInt(key string, fallback int) int {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}

	parsed, err := strconv.Atoi(value)
	if err != nil {
		return fallback
	}

	return parsed
}

func initTracer() func() {
	ctx := context.Background()
	otelEndpoint := normalizeOTLPEndpoint(
		getEnv("OTEL_EXPORTER_OTLP_ENDPOINT", "tempo:4317"),
	)
	serviceName := getEnv("OTEL_SERVICE_NAME", "user-service")

	exporter, err := otlptracegrpc.New(ctx,
		otlptracegrpc.WithEndpoint(otelEndpoint),
		otlptracegrpc.WithInsecure(),
	)
	if err != nil {
		log.Fatalf("failed to init tracer: %v", err)
	}

	res := resource.NewWithAttributes(
		semconv.SchemaURL,
		semconv.ServiceName(serviceName),
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

func waitForConfig(maxAttempts int, delay time.Duration) (*config.Config, error) {
	var cfg *config.Config
	var err error

	for i := 1; i <= maxAttempts; i++ {
		cfg, err = config.LoadConfig()
		if err == nil {
			log.Println("Connected to database")
			return cfg, nil
		}

		log.Printf("Database not ready (attempt %d/%d): %v\n", i, maxAttempts, err)
		time.Sleep(delay)
	}

	return nil, err
}

func runMigrations() {
	cfg, err := waitForConfig(10, 2*time.Second)
	if err != nil {
		log.Fatalf("Cannot connect to database after retries: %v", err)
	}
	defer cfg.DB.Close()

	migrationsDir := getEnv("MIGRATIONS_DIR", filepath.Join("/app", "migrations"))
	log.Printf("Running database migrations from %s", migrationsDir)

	runner := migration.NewRunner(cfg.DB, migrationsDir)
	if err := runner.Run(context.Background()); err != nil {
		log.Fatalf("failed to run migrations: %v", err)
	}

	log.Println("Database migrations completed successfully")
}

func runServer() {
	shutdown := initTracer()
	defer shutdown()

	cfg, err := waitForConfig(10, 2*time.Second)
	if err != nil {
		log.Fatalf("Cannot connect to database after retries: %v", err)
	}
	defer cfg.DB.Close()

	// =========================
	// INIT DEPENDENCIES
	// =========================
	jwtSecret := getEnv("JWT_SECRET", "")
	if strings.TrimSpace(jwtSecret) == "" {
		log.Fatal("JWT_SECRET is required")
	}
	jwtExpiryMinutes := getEnvInt("JWT_EXPIRES_MINUTES", 120)
	refreshExpiryMinutes := getEnvInt("JWT_REFRESH_EXPIRES_MINUTES", 10080)
	maxFailedAttempts := getEnvInt("AUTH_MAX_FAILED_ATTEMPTS", 5)
	failedWindowSeconds := getEnvInt("AUTH_FAILED_ATTEMPT_WINDOW_SECONDS", 900)
	lockoutSeconds := getEnvInt("AUTH_LOCKOUT_SECONDS", 900)

	repo := repository.NewUserRepository(cfg.DB)
	svc := service.NewUserService(
		repo,
		jwtSecret,
		time.Duration(jwtExpiryMinutes)*time.Minute,
		service.AuthPolicy{
			MaxFailedAttempts:   maxFailedAttempts,
			FailedAttemptWindow: time.Duration(failedWindowSeconds) * time.Second,
			LockoutDuration:     time.Duration(lockoutSeconds) * time.Second,
			RefreshTokenTTL:     time.Duration(refreshExpiryMinutes) * time.Minute,
		},
	)

	bootstrapAdminEmail := strings.TrimSpace(os.Getenv("BOOTSTRAP_ADMIN_EMAIL"))
	bootstrapAdminPassword := os.Getenv("BOOTSTRAP_ADMIN_PASSWORD")
	bootstrapAdminName := getEnv("BOOTSTRAP_ADMIN_NAME", "Super Admin")

	if bootstrapAdminEmail != "" || bootstrapAdminPassword != "" {
		if bootstrapAdminEmail == "" || strings.TrimSpace(bootstrapAdminPassword) == "" {
			log.Fatal("BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD must be provided together")
		}
		if err := svc.BootstrapAdmin(bootstrapAdminName, bootstrapAdminEmail, bootstrapAdminPassword); err != nil {
			log.Fatalf("failed to bootstrap admin account: %v", err)
		}
		log.Printf("bootstrap admin account ensured for %s", bootstrapAdminEmail)
	}

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
	r.HandleFunc("/users/refresh", h.RefreshToken).Methods("POST")
	r.HandleFunc("/users/logout", h.Logout).Methods("POST")

	// =========================
	// PUBLIC USER APIs
	// =========================
	r.HandleFunc("/users", h.GetUsers).Methods("GET")
	r.HandleFunc("/users", h.CreateUser).Methods("POST")

	r.HandleFunc("/users/by-email", h.GetUserByEmail).Methods("GET")
	r.HandleFunc("/users/email-exists", h.EmailExists).Methods("GET")
	r.HandleFunc("/users/stats", h.UserStats).Methods("GET")

	r.HandleFunc("/users/{id}", h.GetUserByID).Methods("GET")
	r.HandleFunc("/users/{id}", h.UpdateUser).Methods("PUT")
	r.HandleFunc("/users/{id}", h.DeleteUser).Methods("DELETE")

	r.HandleFunc("/users/{id}/activate", h.ActivateUser).Methods("PATCH")
	r.HandleFunc("/users/{id}/deactivate", h.DeactivateUser).Methods("PATCH")

	// =========================
	// INTERNAL APIs (SERVICE-TO-SERVICE)
	// =========================
	r.HandleFunc("/users/{id}/exists", h.UserExists).Methods("GET")
	r.HandleFunc("/users/{id}/role", h.GetUserRole).Methods("GET")
	r.HandleFunc("/users/{id}/validate", h.ValidateUser).Methods("GET")

	r.HandleFunc("/internal/users/{id}/validate", h.ValidateUser).Methods("GET")

	log.Println("User Service running on :8080")
	log.Fatal(http.ListenAndServe(
		":8080",
		otelhttp.NewHandler(r, "user-service"),
	))
}

func main() {
	if len(os.Args) > 1 {
		switch os.Args[1] {
		case "migrate":
			runMigrations()
			return
		default:
			log.Fatalf("unknown command: %s", os.Args[1])
		}
	}

	runServer()
}
