package service

import (
	"errors"
	"net/mail"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"golang.org/x/crypto/bcrypt"

	"user-service/internal/model"
	"user-service/internal/repository"
)

const (
	defaultPage     = 1
	defaultPageSize = 20
	maxPageSize     = 100
	maxNameLength   = 100
	minPasswordLen  = 6
	maxPasswordLen  = 72
)

var (
	ErrInvalidCredentials = errors.New("invalid credentials")
	ErrEmailAlreadyExists = errors.New("email already exists")
	ErrUserNotFound       = errors.New("user not found")
)

type ValidationError struct {
	Message string
}

func (e *ValidationError) Error() string {
	return e.Message
}

type ListUsersQuery struct {
	Page      int
	PageSize  int
	Role      string
	Search    string
	SortBy    string
	SortOrder string
}

type ListUsersResult struct {
	Items      []model.User
	Page       int
	PageSize   int
	TotalItems int
	TotalPages int
}

type UserService struct {
	repo      *repository.UserRepository
	jwtSecret []byte
	jwtTTL    time.Duration
}

func NewUserService(repo *repository.UserRepository, jwtSecret string, jwtTTL time.Duration) *UserService {
	if jwtTTL <= 0 {
		jwtTTL = 2 * time.Hour
	}

	return &UserService{
		repo:      repo,
		jwtSecret: []byte(jwtSecret),
		jwtTTL:    jwtTTL,
	}
}

func (s *UserService) ListUsers(query ListUsersQuery) (ListUsersResult, error) {
	page, pageSize, err := normalizePage(query.Page, query.PageSize)
	if err != nil {
		return ListUsersResult{}, err
	}

	role := strings.ToUpper(strings.TrimSpace(query.Role))
	if role != "" && !isValidRole(role) {
		return ListUsersResult{}, &ValidationError{Message: "invalid role filter"}
	}

	sortBy := strings.ToLower(strings.TrimSpace(query.SortBy))
	if sortBy != "" && !isValidSortBy(sortBy) {
		return ListUsersResult{}, &ValidationError{Message: "invalid sort_by"}
	}

	sortOrder := strings.ToLower(strings.TrimSpace(query.SortOrder))
	if sortOrder != "" && !isValidSortOrder(sortOrder) {
		return ListUsersResult{}, &ValidationError{Message: "invalid sort_order"}
	}

	result, err := s.repo.ListActiveUsers(repository.ListUsersParams{
		Page:      page,
		PageSize:  pageSize,
		Role:      role,
		Search:    strings.TrimSpace(query.Search),
		SortBy:    sortBy,
		SortOrder: sortOrder,
	})
	if err != nil {
		return ListUsersResult{}, err
	}

	return ListUsersResult{
		Items:      result.Items,
		Page:       page,
		PageSize:   pageSize,
		TotalItems: result.Total,
		TotalPages: calculateTotalPages(result.Total, pageSize),
	}, nil
}

func (s *UserService) CreateUser(user *model.User) error {
	if user == nil {
		return &ValidationError{Message: "user payload is required"}
	}

	name, err := normalizeName(user.Name)
	if err != nil {
		return err
	}
	email, err := normalizeEmail(user.Email)
	if err != nil {
		return err
	}

	role := strings.ToUpper(strings.TrimSpace(user.Role))
	if role == "" {
		role = "CUSTOMER"
	}
	if !isValidRole(role) {
		return &ValidationError{Message: "invalid role"}
	}
	if role == "ADMIN" {
		return &ValidationError{Message: "ADMIN role cannot be self-registered"}
	}

	password := strings.TrimSpace(user.Password)
	if len(password) < minPasswordLen {
		return &ValidationError{Message: "password must be at least 6 characters"}
	}
	if len(password) > maxPasswordLen {
		return &ValidationError{Message: "password is too long"}
	}

	hashedPassword, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return err
	}

	user.Name = name
	user.Email = email
	user.Role = role
	user.Password = string(hashedPassword)

	if err := s.repo.Create(user); err != nil {
		if errors.Is(err, repository.ErrEmailConflict) {
			return ErrEmailAlreadyExists
		}
		return err
	}

	return nil
}

func (s *UserService) Login(email, password string) (*model.User, string, int64, error) {
	normalizedEmail, err := normalizeEmail(email)
	if err != nil {
		return nil, "", 0, &ValidationError{Message: "email is invalid"}
	}

	if strings.TrimSpace(password) == "" {
		return nil, "", 0, &ValidationError{Message: "password is required"}
	}

	user, err := s.repo.FindByEmail(normalizedEmail)
	if err != nil {
		return nil, "", 0, err
	}
	if user == nil {
		return nil, "", 0, ErrInvalidCredentials
	}

	if err := bcrypt.CompareHashAndPassword([]byte(user.Password), []byte(password)); err != nil {
		return nil, "", 0, ErrInvalidCredentials
	}

	accessToken, expiresAt, err := s.generateAccessToken(user)
	if err != nil {
		return nil, "", 0, err
	}

	return user, accessToken, expiresAt.Unix(), nil
}

func (s *UserService) GetUserByID(id string) (*model.User, error) {
	user, err := s.repo.FindByID(id)
	if err != nil {
		return nil, err
	}
	if user == nil {
		return nil, ErrUserNotFound
	}
	return user, nil
}

func (s *UserService) GetUserRole(id string) (string, error) {
	role, err := s.repo.FindRoleByID(id)
	if err != nil {
		return "", err
	}
	if role == "" {
		return "", ErrUserNotFound
	}
	return role, nil
}

func (s *UserService) UpdateUser(id, name, email string) error {
	normalizedName := strings.TrimSpace(name)
	normalizedEmail := strings.TrimSpace(email)

	if normalizedName == "" && normalizedEmail == "" {
		return &ValidationError{Message: "at least one field is required"}
	}

	if normalizedName != "" {
		validName, err := normalizeName(normalizedName)
		if err != nil {
			return err
		}
		normalizedName = validName
	}

	if normalizedEmail != "" {
		validEmail, err := normalizeEmail(normalizedEmail)
		if err != nil {
			return err
		}
		normalizedEmail = validEmail
	}

	if err := s.repo.Update(id, normalizedName, normalizedEmail); err != nil {
		switch {
		case errors.Is(err, repository.ErrUserNotFound):
			return ErrUserNotFound
		case errors.Is(err, repository.ErrEmailConflict):
			return ErrEmailAlreadyExists
		default:
			return err
		}
	}

	return nil
}

func (s *UserService) DeleteUser(id string) error {
	if err := s.repo.SoftDelete(id); err != nil {
		if errors.Is(err, repository.ErrUserNotFound) {
			return ErrUserNotFound
		}
		return err
	}
	return nil
}

func (s *UserService) UserExists(id string) (bool, error) {
	return s.repo.Exists(id)
}

func (s *UserService) GetUserByEmail(email string) (*model.User, error) {
	normalizedEmail, err := normalizeEmail(email)
	if err != nil {
		return nil, err
	}

	user, err := s.repo.FindByEmailPublic(normalizedEmail)
	if err != nil {
		return nil, err
	}
	if user == nil {
		return nil, ErrUserNotFound
	}

	return user, nil
}

func (s *UserService) EmailExists(email string) (bool, error) {
	normalizedEmail, err := normalizeEmail(email)
	if err != nil {
		return false, err
	}

	return s.repo.EmailExists(normalizedEmail)
}

func (s *UserService) ActivateUser(id string) error {
	if err := s.repo.Activate(id); err != nil {
		if errors.Is(err, repository.ErrUserNotFound) {
			return ErrUserNotFound
		}
		return err
	}
	return nil
}

func (s *UserService) DeactivateUser(id string) error {
	if err := s.repo.Deactivate(id); err != nil {
		if errors.Is(err, repository.ErrUserNotFound) {
			return ErrUserNotFound
		}
		return err
	}
	return nil
}

func (s *UserService) UserStats() (map[string]any, error) {
	return s.repo.Stats()
}

func (s *UserService) ValidateUser(id string) (bool, string, bool, error) {
	return s.repo.ValidateUser(id)
}

func (s *UserService) generateAccessToken(user *model.User) (string, time.Time, error) {
	if len(s.jwtSecret) == 0 {
		return "", time.Time{}, errors.New("jwt secret is empty")
	}

	now := time.Now().UTC()
	expiresAt := now.Add(s.jwtTTL)

	claims := jwt.MapClaims{
		"sub":   user.ID,
		"email": user.Email,
		"name":  user.Name,
		"role":  user.Role,
		"iat":   now.Unix(),
		"exp":   expiresAt.Unix(),
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	signedToken, err := token.SignedString(s.jwtSecret)
	if err != nil {
		return "", time.Time{}, err
	}

	return signedToken, expiresAt, nil
}

func isValidRole(role string) bool {
	switch role {
	case "CUSTOMER", "SELLER", "ADMIN":
		return true
	default:
		return false
	}
}

func isValidSortBy(sortBy string) bool {
	switch sortBy {
	case "name", "email", "role", "created_at":
		return true
	default:
		return false
	}
}

func isValidSortOrder(sortOrder string) bool {
	return sortOrder == "asc" || sortOrder == "desc"
}

func normalizeName(raw string) (string, error) {
	name := strings.TrimSpace(raw)
	if name == "" {
		return "", &ValidationError{Message: "name is required"}
	}
	if len(name) > maxNameLength {
		return "", &ValidationError{Message: "name is too long"}
	}
	return name, nil
}

func normalizeEmail(raw string) (string, error) {
	email := strings.ToLower(strings.TrimSpace(raw))
	if email == "" {
		return "", &ValidationError{Message: "email is required"}
	}

	parsed, err := mail.ParseAddress(email)
	if err != nil || !strings.EqualFold(parsed.Address, email) {
		return "", &ValidationError{Message: "email is invalid"}
	}

	return email, nil
}

func normalizePage(page, pageSize int) (int, int, error) {
	if page <= 0 {
		page = defaultPage
	}
	if pageSize <= 0 {
		pageSize = defaultPageSize
	}
	if pageSize > maxPageSize {
		return 0, 0, &ValidationError{Message: "page_size exceeds maximum allowed value"}
	}
	return page, pageSize, nil
}

func calculateTotalPages(total, pageSize int) int {
	if total == 0 {
		return 0
	}
	return (total + pageSize - 1) / pageSize
}
