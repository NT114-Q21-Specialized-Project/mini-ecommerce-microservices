package config

import (
	"database/sql"
	"fmt"
	"os"
	"strconv"
	"strings"

	_ "github.com/lib/pq"
)

type Config struct {
	DB                    *sql.DB
	Port                  string
	ProductServiceBaseURL string
	ChaosMode             bool
	LatencyProbability    float64
	ErrorProbability      float64
	ChaosDelayMs          int
}

func getEnv(key, fallback string) string {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	return value
}

func getEnvBool(key string, fallback bool) bool {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	parsed, err := strconv.ParseBool(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func getEnvFloat(key string, fallback float64) float64 {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	parsed, err := strconv.ParseFloat(value, 64)
	if err != nil {
		return fallback
	}
	if parsed < 0 {
		return 0
	}
	if parsed > 1 {
		return 1
	}
	return parsed
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

func Load() (*Config, error) {
	dbHost := getEnv("DB_HOST", "inventory-db")
	dbPort := getEnv("DB_PORT", "5432")
	dbName := getEnv("DB_NAME", "inventory_db")
	dbUser := getEnv("DB_USER", "inventory")
	dbPassword := getEnv("DB_PASSWORD", "inventorypass")

	dsn := fmt.Sprintf(
		"host=%s port=%s user=%s password=%s dbname=%s sslmode=disable",
		dbHost, dbPort, dbUser, dbPassword, dbName,
	)

	db, err := sql.Open("postgres", dsn)
	if err != nil {
		return nil, err
	}

	if err := db.Ping(); err != nil {
		return nil, err
	}

	cfg := &Config{
		DB:                    db,
		Port:                  getEnv("SERVER_PORT", "8080"),
		ProductServiceBaseURL: strings.TrimRight(getEnv("PRODUCT_SERVICE_BASE_URL", "http://product-service:8080"), "/"),
		ChaosMode:             getEnvBool("CHAOS_MODE", false),
		LatencyProbability:    getEnvFloat("LATENCY_PROBABILITY", 0),
		ErrorProbability:      getEnvFloat("ERROR_PROBABILITY", 0),
		ChaosDelayMs:          getEnvInt("CHAOS_DELAY_MS", 0),
	}

	return cfg, nil
}
