package main

import (
	"log"
	"net/http"
	"time"

	"github.com/gorilla/mux"

	"user-service/internal/config"
	"user-service/internal/handler"
	"user-service/internal/repository"
	"user-service/internal/service"
)

func main() {
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
	r.HandleFunc("/health", h.Health).Methods("GET")
	r.HandleFunc("/users", h.GetUsers).Methods("GET")
	r.HandleFunc("/users", h.CreateUser).Methods("POST")
	r.HandleFunc("/users/{id}", h.GetUserByID).Methods("GET")

	log.Println("User Service running on :8080")
	log.Fatal(http.ListenAndServe(":8080", r))
}
