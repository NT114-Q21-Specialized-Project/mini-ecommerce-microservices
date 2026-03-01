package handler

import (
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
	"strings"

	"github.com/google/uuid"
	"github.com/gorilla/mux"

	"user-service/internal/dto"
	"user-service/internal/model"
	"user-service/internal/service"
)

type UserHandler struct {
	service *service.UserService
}

func NewUserHandler(s *service.UserService) *UserHandler {
	return &UserHandler{service: s}
}

func (h *UserHandler) Health(w http.ResponseWriter, _ *http.Request) {
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte("OK"))
}

func (h *UserHandler) Login(w http.ResponseWriter, r *http.Request) {
	var req dto.LoginRequest
	if err := decodeJSON(r, &req); err != nil {
		writeError(w, r, http.StatusBadRequest, "INVALID_REQUEST", err.Error())
		return
	}

	user, accessToken, expiresAt, err := h.service.Login(req.Email, req.Password)
	if err != nil {
		h.handleServiceError(w, r, err)
		return
	}

	writeJSON(w, http.StatusOK, dto.LoginResponse{
		AccessToken: accessToken,
		TokenType:   "Bearer",
		ExpiresAt:   expiresAt,
		User:        toUserResponse(*user),
	})
}

func (h *UserHandler) GetUsers(w http.ResponseWriter, r *http.Request) {
	page, err := queryInt(r, "page", 1)
	if err != nil {
		writeError(w, r, http.StatusBadRequest, "VALIDATION_ERROR", "page must be a positive integer")
		return
	}

	pageSize, err := queryInt(r, "page_size", 20)
	if err != nil {
		writeError(w, r, http.StatusBadRequest, "VALIDATION_ERROR", "page_size must be a positive integer")
		return
	}

	result, err := h.service.ListUsers(service.ListUsersQuery{
		Page:      page,
		PageSize:  pageSize,
		Role:      r.URL.Query().Get("role"),
		Search:    r.URL.Query().Get("search"),
		SortBy:    r.URL.Query().Get("sort_by"),
		SortOrder: r.URL.Query().Get("sort_order"),
	})
	if err != nil {
		h.handleServiceError(w, r, err)
		return
	}

	items := make([]dto.UserResponse, 0, len(result.Items))
	for _, user := range result.Items {
		items = append(items, toUserResponse(user))
	}

	writeJSON(w, http.StatusOK, dto.ListUsersResponse{
		Items: items,
		Pagination: dto.PaginationMeta{
			Page:       result.Page,
			PageSize:   result.PageSize,
			TotalItems: result.TotalItems,
			TotalPages: result.TotalPages,
		},
	})
}

func (h *UserHandler) GetUserByEmail(w http.ResponseWriter, r *http.Request) {
	email := r.URL.Query().Get("email")
	if strings.TrimSpace(email) == "" {
		writeError(w, r, http.StatusBadRequest, "VALIDATION_ERROR", "email is required")
		return
	}

	user, err := h.service.GetUserByEmail(email)
	if err != nil {
		h.handleServiceError(w, r, err)
		return
	}

	writeJSON(w, http.StatusOK, toUserResponse(*user))
}

func (h *UserHandler) EmailExists(w http.ResponseWriter, r *http.Request) {
	email := r.URL.Query().Get("email")
	if strings.TrimSpace(email) == "" {
		writeError(w, r, http.StatusBadRequest, "VALIDATION_ERROR", "email is required")
		return
	}

	exists, err := h.service.EmailExists(email)
	if err != nil {
		h.handleServiceError(w, r, err)
		return
	}

	writeJSON(w, http.StatusOK, map[string]bool{"exists": exists})
}

func (h *UserHandler) CreateUser(w http.ResponseWriter, r *http.Request) {
	var req dto.RegisterUserRequest
	if err := decodeJSON(r, &req); err != nil {
		writeError(w, r, http.StatusBadRequest, "INVALID_REQUEST", err.Error())
		return
	}

	user := model.User{
		Name:     req.Name,
		Email:    req.Email,
		Password: req.Password,
		Role:     req.Role,
	}

	if err := h.service.CreateUser(&user); err != nil {
		h.handleServiceError(w, r, err)
		return
	}

	writeJSON(w, http.StatusCreated, toUserResponse(user))
}

func (h *UserHandler) GetUserByID(w http.ResponseWriter, r *http.Request) {
	id, err := pathUUID(r, "id")
	if err != nil {
		writeError(w, r, http.StatusBadRequest, "VALIDATION_ERROR", err.Error())
		return
	}

	user, err := h.service.GetUserByID(id)
	if err != nil {
		h.handleServiceError(w, r, err)
		return
	}

	writeJSON(w, http.StatusOK, toUserResponse(*user))
}

func (h *UserHandler) GetUserRole(w http.ResponseWriter, r *http.Request) {
	id, err := pathUUID(r, "id")
	if err != nil {
		writeError(w, r, http.StatusBadRequest, "VALIDATION_ERROR", err.Error())
		return
	}

	role, err := h.service.GetUserRole(id)
	if err != nil {
		h.handleServiceError(w, r, err)
		return
	}

	writeJSON(w, http.StatusOK, map[string]string{
		"id":   id,
		"role": role,
	})
}

func (h *UserHandler) UpdateUser(w http.ResponseWriter, r *http.Request) {
	id, err := pathUUID(r, "id")
	if err != nil {
		writeError(w, r, http.StatusBadRequest, "VALIDATION_ERROR", err.Error())
		return
	}

	var req dto.UpdateUserRequest
	if err := decodeJSON(r, &req); err != nil {
		writeError(w, r, http.StatusBadRequest, "INVALID_REQUEST", err.Error())
		return
	}
	if req.Name == nil && req.Email == nil {
		writeError(w, r, http.StatusBadRequest, "VALIDATION_ERROR", "at least one field is required")
		return
	}

	name := ""
	if req.Name != nil {
		name = *req.Name
	}
	email := ""
	if req.Email != nil {
		email = *req.Email
	}

	if err := h.service.UpdateUser(id, name, email); err != nil {
		h.handleServiceError(w, r, err)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func (h *UserHandler) ActivateUser(w http.ResponseWriter, r *http.Request) {
	id, err := pathUUID(r, "id")
	if err != nil {
		writeError(w, r, http.StatusBadRequest, "VALIDATION_ERROR", err.Error())
		return
	}

	if err := h.service.ActivateUser(id); err != nil {
		h.handleServiceError(w, r, err)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func (h *UserHandler) DeactivateUser(w http.ResponseWriter, r *http.Request) {
	id, err := pathUUID(r, "id")
	if err != nil {
		writeError(w, r, http.StatusBadRequest, "VALIDATION_ERROR", err.Error())
		return
	}

	if err := h.service.DeactivateUser(id); err != nil {
		h.handleServiceError(w, r, err)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func (h *UserHandler) UserStats(w http.ResponseWriter, r *http.Request) {
	stats, err := h.service.UserStats()
	if err != nil {
		h.handleServiceError(w, r, err)
		return
	}

	writeJSON(w, http.StatusOK, stats)
}

func (h *UserHandler) UserExists(w http.ResponseWriter, r *http.Request) {
	id, err := pathUUID(r, "id")
	if err != nil {
		writeError(w, r, http.StatusBadRequest, "VALIDATION_ERROR", err.Error())
		return
	}

	exists, err := h.service.UserExists(id)
	if err != nil {
		h.handleServiceError(w, r, err)
		return
	}

	writeJSON(w, http.StatusOK, map[string]bool{"exists": exists})
}

func (h *UserHandler) ValidateUser(w http.ResponseWriter, r *http.Request) {
	id, err := pathUUID(r, "id")
	if err != nil {
		writeError(w, r, http.StatusBadRequest, "VALIDATION_ERROR", err.Error())
		return
	}

	valid, role, active, err := h.service.ValidateUser(id)
	if err != nil {
		h.handleServiceError(w, r, err)
		return
	}

	writeJSON(w, http.StatusOK, map[string]any{
		"valid":    valid,
		"id":       id,
		"role":     role,
		"isActive": active,
	})
}

func (h *UserHandler) DeleteUser(w http.ResponseWriter, r *http.Request) {
	id, err := pathUUID(r, "id")
	if err != nil {
		writeError(w, r, http.StatusBadRequest, "VALIDATION_ERROR", err.Error())
		return
	}

	if err := h.service.DeleteUser(id); err != nil {
		h.handleServiceError(w, r, err)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func (h *UserHandler) handleServiceError(w http.ResponseWriter, r *http.Request, err error) {
	var validationErr *service.ValidationError

	switch {
	case errors.As(err, &validationErr):
		writeError(w, r, http.StatusBadRequest, "VALIDATION_ERROR", validationErr.Message)
	case errors.Is(err, service.ErrInvalidCredentials):
		writeError(w, r, http.StatusUnauthorized, "UNAUTHORIZED", "invalid email or password")
	case errors.Is(err, service.ErrEmailAlreadyExists):
		writeError(w, r, http.StatusConflict, "CONFLICT", "email already exists")
	case errors.Is(err, service.ErrUserNotFound):
		writeError(w, r, http.StatusNotFound, "NOT_FOUND", "user not found")
	default:
		writeError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "internal server error")
	}
}

func decodeJSON(r *http.Request, target any) error {
	if r == nil || r.Body == nil {
		return errors.New("request body is required")
	}

	decoder := json.NewDecoder(r.Body)
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil {
		return errors.New("invalid request body")
	}

	if decoder.More() {
		return errors.New("invalid request body")
	}

	return nil
}

func toUserResponse(user model.User) dto.UserResponse {
	return dto.UserResponse{
		ID:        user.ID,
		Name:      user.Name,
		Email:     user.Email,
		Role:      user.Role,
		IsActive:  user.IsActive,
		CreatedAt: user.CreatedAt,
	}
}

func pathUUID(r *http.Request, key string) (string, error) {
	id := strings.TrimSpace(mux.Vars(r)[key])
	if id == "" {
		return "", errors.New("id is required")
	}

	if _, err := uuid.Parse(id); err != nil {
		return "", errors.New("id must be a valid UUID")
	}

	return id, nil
}

func queryInt(r *http.Request, key string, defaultValue int) (int, error) {
	raw := strings.TrimSpace(r.URL.Query().Get(key))
	if raw == "" {
		return defaultValue, nil
	}

	value, err := strconv.Atoi(raw)
	if err != nil || value <= 0 {
		return 0, errors.New("invalid integer")
	}

	return value, nil
}
