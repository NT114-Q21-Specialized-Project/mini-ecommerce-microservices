package service

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"net/mail"
	"strings"
	"time"
	"unicode"

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
	minPasswordLen  = 10
	maxPasswordLen  = 72
)

var (
	ErrInvalidCredentials  = errors.New("invalid credentials")
	ErrEmailAlreadyExists  = errors.New("email already exists")
	ErrUserNotFound        = errors.New("user not found")
	ErrAccountLocked       = errors.New("account temporarily locked")
	ErrInvalidRefreshToken = errors.New("invalid refresh token")
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

type AuthPolicy struct {
	MaxFailedAttempts   int
	FailedAttemptWindow time.Duration
	LockoutDuration     time.Duration
	RefreshTokenTTL     time.Duration
}

type userRepository interface {
	ListActiveUsers(params repository.ListUsersParams) (repository.ListUsersResult, error)
	Create(user *model.User) error
	FindByEmail(email string) (*model.User, error)
	FindByID(id string) (*model.User, error)
	FindRoleByID(id string) (string, error)
	Update(id, name, email string) error
	SoftDelete(id string) error
	Exists(id string) (bool, error)
	FindByEmailPublic(email string) (*model.User, error)
	FindByEmailAnyStatus(email string) (*model.User, error)
	EmailExists(email string) (bool, error)
	Activate(id string) error
	Deactivate(id string) error
	Stats() (map[string]any, error)
	ValidateUser(id string) (bool, string, bool, error)
	RegisterFailedLoginAttempt(userID string, now time.Time, window time.Duration, maxAttempts int, lockoutDuration time.Duration) (bool, error)
	ResetFailedLoginAttempts(userID string) error
	StoreRefreshToken(userID, tokenHash string, expiresAt time.Time) error
	RotateRefreshToken(oldTokenHash, newTokenHash string, newExpiresAt, now time.Time) (string, error)
	RevokeRefreshToken(tokenHash string, now time.Time) error
}

type UserService struct {
	repo       userRepository
	jwtSecret  []byte
	jwtTTL     time.Duration
	authPolicy AuthPolicy
	nowFn      func() time.Time
}

func NewUserService(repo userRepository, jwtSecret string, jwtTTL time.Duration, policy AuthPolicy) *UserService {
	if jwtTTL <= 0 {
		jwtTTL = 2 * time.Hour
	}

	if policy.MaxFailedAttempts <= 0 {
		policy.MaxFailedAttempts = 5
	}
	if policy.FailedAttemptWindow <= 0 {
		policy.FailedAttemptWindow = 15 * time.Minute
	}
	if policy.LockoutDuration <= 0 {
		policy.LockoutDuration = 15 * time.Minute
	}
	if policy.RefreshTokenTTL <= 0 {
		policy.RefreshTokenTTL = 7 * 24 * time.Hour
	}

	return &UserService{
		repo:       repo,
		jwtSecret:  []byte(jwtSecret),
		jwtTTL:     jwtTTL,
		authPolicy: policy,
		nowFn:      time.Now,
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

	if err := validatePasswordStrength(user.Password); err != nil {
		return err
	}

	hashedPassword, err := bcrypt.GenerateFromPassword([]byte(user.Password), bcrypt.DefaultCost)
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

func (s *UserService) BootstrapAdmin(name, email, password string) error {
	normalizedEmail, err := normalizeEmail(email)
	if err != nil {
		return err
	}

	if err := validatePasswordStrength(password); err != nil {
		return err
	}

	normalizedName, err := normalizeName(name)
	if err != nil {
		return err
	}

	existing, err := s.repo.FindByEmailAnyStatus(normalizedEmail)
	if err != nil {
		return err
	}

	if existing != nil {
		if !strings.EqualFold(existing.Role, "ADMIN") {
			return &ValidationError{Message: "bootstrap admin email already belongs to non-admin user"}
		}
		if !existing.IsActive {
			if err := s.repo.Activate(existing.ID); err != nil {
				return err
			}
		}
		return nil
	}

	hashedPassword, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return err
	}

	admin := &model.User{
		Name:     normalizedName,
		Email:    normalizedEmail,
		Password: string(hashedPassword),
		Role:     "ADMIN",
	}

	if err := s.repo.Create(admin); err != nil {
		if errors.Is(err, repository.ErrEmailConflict) {
			existing, findErr := s.repo.FindByEmailAnyStatus(normalizedEmail)
			if findErr != nil {
				return findErr
			}
			if existing != nil && strings.EqualFold(existing.Role, "ADMIN") {
				return nil
			}
			return &ValidationError{Message: "bootstrap admin email already exists with non-admin role"}
		}
		return err
	}

	return nil
}

func (s *UserService) Login(email, password string) (*model.User, string, int64, string, int64, error) {
	normalizedEmail, err := normalizeEmail(email)
	if err != nil {
		return nil, "", 0, "", 0, &ValidationError{Message: "email is invalid"}
	}

	if strings.TrimSpace(password) == "" {
		return nil, "", 0, "", 0, &ValidationError{Message: "password is required"}
	}

	user, err := s.repo.FindByEmail(normalizedEmail)
	if err != nil {
		return nil, "", 0, "", 0, err
	}
	if user == nil {
		return nil, "", 0, "", 0, ErrInvalidCredentials
	}

	now := s.nowUTC()
	if user.LockedUntil != nil && user.LockedUntil.After(now) {
		return nil, "", 0, "", 0, ErrAccountLocked
	}

	if err := bcrypt.CompareHashAndPassword([]byte(user.Password), []byte(password)); err != nil {
		locked, lockErr := s.repo.RegisterFailedLoginAttempt(
			user.ID,
			now,
			s.authPolicy.FailedAttemptWindow,
			s.authPolicy.MaxFailedAttempts,
			s.authPolicy.LockoutDuration,
		)
		if lockErr != nil {
			return nil, "", 0, "", 0, lockErr
		}
		if locked {
			return nil, "", 0, "", 0, ErrAccountLocked
		}
		return nil, "", 0, "", 0, ErrInvalidCredentials
	}

	if err := s.repo.ResetFailedLoginAttempts(user.ID); err != nil {
		return nil, "", 0, "", 0, err
	}

	accessToken, accessExpiresAt, err := s.generateAccessToken(user, now)
	if err != nil {
		return nil, "", 0, "", 0, err
	}

	refreshToken, refreshTokenHash, err := generateRefreshTokenPair()
	if err != nil {
		return nil, "", 0, "", 0, err
	}

	refreshExpiresAt := now.Add(s.authPolicy.RefreshTokenTTL)
	if err := s.repo.StoreRefreshToken(user.ID, refreshTokenHash, refreshExpiresAt); err != nil {
		return nil, "", 0, "", 0, err
	}

	return user, accessToken, accessExpiresAt.Unix(), refreshToken, refreshExpiresAt.Unix(), nil
}

func (s *UserService) RefreshToken(refreshToken string) (*model.User, string, int64, string, int64, error) {
	token := strings.TrimSpace(refreshToken)
	if token == "" {
		return nil, "", 0, "", 0, &ValidationError{Message: "refresh_token is required"}
	}

	now := s.nowUTC()
	oldTokenHash := hashToken(token)

	newRefreshToken, newRefreshTokenHash, err := generateRefreshTokenPair()
	if err != nil {
		return nil, "", 0, "", 0, err
	}
	newRefreshExpiresAt := now.Add(s.authPolicy.RefreshTokenTTL)

	userID, err := s.repo.RotateRefreshToken(oldTokenHash, newRefreshTokenHash, newRefreshExpiresAt, now)
	if err != nil {
		switch {
		case errors.Is(err, repository.ErrRefreshTokenNotFound), errors.Is(err, repository.ErrRefreshTokenExpired), errors.Is(err, repository.ErrRefreshTokenRevoked):
			return nil, "", 0, "", 0, ErrInvalidRefreshToken
		default:
			return nil, "", 0, "", 0, err
		}
	}

	user, err := s.repo.FindByID(userID)
	if err != nil {
		return nil, "", 0, "", 0, err
	}
	if user == nil {
		_ = s.repo.RevokeRefreshToken(newRefreshTokenHash, now)
		return nil, "", 0, "", 0, ErrInvalidRefreshToken
	}

	accessToken, accessExpiresAt, err := s.generateAccessToken(user, now)
	if err != nil {
		return nil, "", 0, "", 0, err
	}

	return user, accessToken, accessExpiresAt.Unix(), newRefreshToken, newRefreshExpiresAt.Unix(), nil
}

func (s *UserService) RevokeRefreshToken(refreshToken string) error {
	token := strings.TrimSpace(refreshToken)
	if token == "" {
		return &ValidationError{Message: "refresh_token is required"}
	}

	now := s.nowUTC()
	err := s.repo.RevokeRefreshToken(hashToken(token), now)
	if errors.Is(err, repository.ErrRefreshTokenNotFound) {
		return nil
	}
	return err
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

func (s *UserService) generateAccessToken(user *model.User, now time.Time) (string, time.Time, error) {
	if len(s.jwtSecret) == 0 {
		return "", time.Time{}, errors.New("jwt secret is empty")
	}

	expiresAt := now.Add(s.jwtTTL)

	claims := jwt.MapClaims{
		"sub":  user.ID,
		"role": user.Role,
		"iat":  now.Unix(),
		"exp":  expiresAt.Unix(),
		"jti":  generateTokenID(),
		"typ":  "access",
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	signedToken, err := token.SignedString(s.jwtSecret)
	if err != nil {
		return "", time.Time{}, err
	}

	return signedToken, expiresAt, nil
}

func (s *UserService) nowUTC() time.Time {
	if s.nowFn == nil {
		return time.Now().UTC()
	}
	return s.nowFn().UTC()
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

func validatePasswordStrength(password string) error {
	if len(password) < minPasswordLen {
		return &ValidationError{Message: "password must be at least 10 characters"}
	}
	if len(password) > maxPasswordLen {
		return &ValidationError{Message: "password is too long"}
	}

	var hasUpper, hasLower, hasDigit, hasSpecial bool
	for _, ch := range password {
		switch {
		case unicode.IsUpper(ch):
			hasUpper = true
		case unicode.IsLower(ch):
			hasLower = true
		case unicode.IsDigit(ch):
			hasDigit = true
		case unicode.IsPunct(ch) || unicode.IsSymbol(ch):
			hasSpecial = true
		}
	}

	if !hasUpper || !hasLower || !hasDigit || !hasSpecial {
		return &ValidationError{Message: "password must include upper, lower, digit, and special character"}
	}

	return nil
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

func generateRefreshTokenPair() (string, string, error) {
	raw := make([]byte, 32)
	if _, err := rand.Read(raw); err != nil {
		return "", "", err
	}

	token := base64.RawURLEncoding.EncodeToString(raw)
	return token, hashToken(token), nil
}

func hashToken(raw string) string {
	sum := sha256.Sum256([]byte(raw))
	return hex.EncodeToString(sum[:])
}

func generateTokenID() string {
	raw := make([]byte, 16)
	if _, err := rand.Read(raw); err != nil {
		return base64.RawURLEncoding.EncodeToString([]byte(time.Now().UTC().String()))
	}
	return base64.RawURLEncoding.EncodeToString(raw)
}
