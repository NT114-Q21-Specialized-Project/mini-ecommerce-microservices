package handler

import (
	"encoding/json"
	"net/http"

	"user-service/internal/model"
	"user-service/internal/service"

	"github.com/gorilla/mux"
)

type UserHandler struct {
	service *service.UserService
}

func NewUserHandler(s *service.UserService) *UserHandler {
	return &UserHandler{service: s}
}

// =========================
// HEALTH CHECK
// =========================
func (h *UserHandler) Health(w http.ResponseWriter, _ *http.Request) {
	w.WriteHeader(http.StatusOK)
	w.Write([]byte("OK"))
}

// =========================
// LOGIN HANDLER (NEW)
// =========================
func (h *UserHandler) Login(w http.ResponseWriter, r *http.Request) {
	var creds struct {
		Email    string `json:"email"`
		Password string `json:"password"`
	}

	if err := json.NewDecoder(r.Body).Decode(&creds); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	user, accessToken, expiresAt, err := h.service.Login(creds.Email, creds.Password)
	if err != nil {
		http.Error(w, err.Error(), http.StatusUnauthorized)
		return
	}

	user.Password = "" // Đảm bảo không gửi password hash về
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]any{
		"access_token": accessToken,
		"token_type":   "Bearer",
		"expires_at":   expiresAt,
		"user":         user,
	})
}

// =========================
// GET ALL USERS
// =========================
func (h *UserHandler) GetUsers(w http.ResponseWriter, _ *http.Request) {
	users, err := h.service.GetUsers()
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(users)
}

// =========================
// GET USER BY EMAIL
// =========================
func (h *UserHandler) GetUserByEmail(w http.ResponseWriter, r *http.Request) {
	email := r.URL.Query().Get("email")
	if email == "" {
		http.Error(w, "email is required", http.StatusBadRequest)
		return
	}

	user, err := h.service.GetUserByEmail(email)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if user == nil {
		http.Error(w, "User not found", http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(user)
}

// =========================
// EMAIL EXISTS
// =========================
func (h *UserHandler) EmailExists(w http.ResponseWriter, r *http.Request) {
	email := r.URL.Query().Get("email")
	if email == "" {
		http.Error(w, "email is required", http.StatusBadRequest)
		return
	}

	exists, err := h.service.EmailExists(email)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]bool{
		"exists": exists,
	})
}

// =========================
// CREATE USER / REGISTER
// =========================
func (h *UserHandler) CreateUser(w http.ResponseWriter, r *http.Request) {
	var user model.User

	if err := json.NewDecoder(r.Body).Decode(&user); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if err := h.service.CreateUser(&user); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	user.Password = "" // Bảo mật
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(user)
}

// =========================
// GET USER BY ID
// =========================
func (h *UserHandler) GetUserByID(w http.ResponseWriter, r *http.Request) {
	id := mux.Vars(r)["id"]

	user, err := h.service.GetUserByID(id)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	if user == nil {
		http.Error(w, "User not found", http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(user)
}

// =========================
// INTERNAL API – GET USER ROLE
// =========================
func (h *UserHandler) GetUserRole(w http.ResponseWriter, r *http.Request) {
	id := mux.Vars(r)["id"]

	role, err := h.service.GetUserRole(id)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	if role == "" {
		http.Error(w, "User not found", http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{
		"id":   id,
		"role": role,
	})
}

// =========================
// UPDATE USER
// =========================
func (h *UserHandler) UpdateUser(w http.ResponseWriter, r *http.Request) {
	id := mux.Vars(r)["id"]

	var payload struct {
		Name  *string `json:"name"`
		Email *string `json:"email"`
	}

	if err := json.NewDecoder(r.Body).Decode(&payload); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	// Không có field nào để update
	if payload.Name == nil && payload.Email == nil {
		http.Error(w, "Nothing to update", http.StatusBadRequest)
		return
	}

	nameVal := ""
	if payload.Name != nil {
		nameVal = *payload.Name
	}

	emailVal := ""
	if payload.Email != nil {
		emailVal = *payload.Email
	}

	if err := h.service.UpdateUser(id, nameVal, emailVal); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// =========================
// ACTIVATE USER
// =========================
func (h *UserHandler) ActivateUser(w http.ResponseWriter, r *http.Request) {
	id := mux.Vars(r)["id"]

	if err := h.service.ActivateUser(id); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// =========================
// DEACTIVATE USER
// =========================
func (h *UserHandler) DeactivateUser(w http.ResponseWriter, r *http.Request) {
	id := mux.Vars(r)["id"]

	if err := h.service.DeactivateUser(id); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// =========================
// USER STATS
// =========================
func (h *UserHandler) UserStats(w http.ResponseWriter, _ *http.Request) {
	stats, err := h.service.UserStats()
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(stats)
}

// =========================
// INTERNAL API – USER EXISTS
// =========================
func (h *UserHandler) UserExists(w http.ResponseWriter, r *http.Request) {
	id := mux.Vars(r)["id"]

	exists, err := h.service.UserExists(id)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]bool{
		"exists": exists,
	})
}

// =========================
// INTERNAL API – VALIDATE USER
// =========================
func (h *UserHandler) ValidateUser(w http.ResponseWriter, r *http.Request) {
	id := mux.Vars(r)["id"]

	valid, role, active, err := h.service.ValidateUser(id)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]any{
		"valid":    valid,
		"id":       id,
		"role":     role,
		"isActive": active,
	})
}

// =========================
// DELETE USER (SOFT DELETE)
// =========================
func (h *UserHandler) DeleteUser(w http.ResponseWriter, r *http.Request) {
	id := mux.Vars(r)["id"]

	if err := h.service.DeleteUser(id); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}
