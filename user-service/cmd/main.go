package main

import (
	"log"
	"net/http"

	"github.com/gorilla/mux"

	"user-service/internal/config"
	"user-service/internal/handler"
	"user-service/internal/repository"
	"user-service/internal/service"
)

func main() {
	cfg, err := config.LoadConfig()
	if err != nil {
		log.Fatalf("cannot load config: %v", err)
	}

	repo := repository.NewUserRepository(cfg.DB)
	svc := service.NewUserService(repo)
	h := handler.NewUserHandler(svc)

	r := mux.NewRouter()
	r.HandleFunc("/health", h.Health).Methods("GET")
	r.HandleFunc("/users", h.GetUsers).Methods("GET")
	r.HandleFunc("/users", h.CreateUser).Methods("POST")

	log.Println("User Service running on :8080")
	log.Fatal(http.ListenAndServe(":8080", r))
}
