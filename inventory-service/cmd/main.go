package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"strings"
	"time"

	"inventory-service/internal/config"
	"inventory-service/internal/event"
	"inventory-service/internal/handler"
	"inventory-service/internal/middleware"
	"inventory-service/internal/repository"
	"inventory-service/internal/service"

	"github.com/gorilla/mux"
	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.21.0"
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

func initTracer() func() {
	ctx := context.Background()
	endpoint := normalizeOTLPEndpoint(getEnv("OTEL_EXPORTER_OTLP_ENDPOINT", "tempo:4317"))
	serviceName := getEnv("OTEL_SERVICE_NAME", "inventory-service")

	exporter, err := otlptracegrpc.New(ctx, otlptracegrpc.WithEndpoint(endpoint), otlptracegrpc.WithInsecure())
	if err != nil {
		log.Fatalf("failed to init tracer: %v", err)
	}

	res := resource.NewWithAttributes(semconv.SchemaURL, semconv.ServiceName(serviceName))
	tp := sdktrace.NewTracerProvider(sdktrace.WithBatcher(exporter), sdktrace.WithResource(res))
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
	for attempt := 1; attempt <= 10; attempt++ {
		cfg, err = config.Load()
		if err == nil {
			break
		}
		log.Printf("database not ready (attempt %d/10): %v", attempt, err)
		time.Sleep(2 * time.Second)
	}
	if err != nil {
		log.Fatalf("cannot connect to inventory database: %v", err)
	}

	repo, err := repository.NewInventoryRepository(cfg.DB)
	if err != nil {
		log.Fatalf("failed to initialize inventory repository: %v", err)
	}

	inventoryService := service.NewInventoryService(repo, cfg)
	inventoryHandler := handler.NewInventoryHandler(inventoryService)

	appCtx, cancel := context.WithCancel(context.Background())
	defer cancel()

	redisAddr := getEnv("REDIS_ADDR", "redis:6379")
	redisChannel := getEnv("ORDER_EVENTS_CHANNEL", "orders.events")
	subscriber, subscriberErr := event.StartOrderEventSubscriber(appCtx, redisAddr, redisChannel)
	if subscriberErr != nil {
		log.Printf("inventory event subscriber is disabled: %v", subscriberErr)
	} else {
		defer subscriber.Close()
	}

	router := mux.NewRouter()
	router.HandleFunc("/health", inventoryHandler.Health).Methods(http.MethodGet)
	router.HandleFunc("/inventory/health", inventoryHandler.Health).Methods(http.MethodGet)
	router.HandleFunc("/inventory/reserve", inventoryHandler.Reserve).Methods(http.MethodPost)
	router.HandleFunc("/inventory/release", inventoryHandler.Release).Methods(http.MethodPost)
	router.HandleFunc("/simulate-cpu", inventoryHandler.SimulateCPU).Methods(http.MethodGet)
	router.HandleFunc("/simulate-memory", inventoryHandler.SimulateMemory).Methods(http.MethodGet)
	router.HandleFunc("/inventory/simulate-cpu", inventoryHandler.SimulateCPU).Methods(http.MethodGet)
	router.HandleFunc("/inventory/simulate-memory", inventoryHandler.SimulateMemory).Methods(http.MethodGet)
	router.HandleFunc("/inventory/{productId}", inventoryHandler.GetInventory).Methods(http.MethodGet)

	handlerChain := middleware.Correlation(middleware.RequestLogger(router))
	wrapped := otelhttp.NewHandler(handlerChain, "inventory-service")

	log.Printf("Inventory Service running on :%s", cfg.Port)
	log.Fatal(http.ListenAndServe(":"+cfg.Port, wrapped))
}
