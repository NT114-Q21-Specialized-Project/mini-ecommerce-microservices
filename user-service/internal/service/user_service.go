package service

import (
	"errors"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"golang.org/x/crypto/bcrypt"

	"user-service/internal/model"
	"user-service/internal/repository"
)

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

func (s *UserService) GetUsers() ([]model.User, error) {
	return s.repo.FindAll()
}

// =========================
// REGISTER / CREATE USER (UPDATED WITH HASHING)
// =========================
func (s *UserService) CreateUser(user *model.User) error {

	if user.Role == "" {
		user.Role = "CUSTOMER"
	}

	if !isValidRole(user.Role) {
		return errors.New("invalid role")
	}

	// Mã hóa mật khẩu
	hashedPassword, err := bcrypt.GenerateFromPassword([]byte(user.Password), bcrypt.DefaultCost)
	if err != nil {
		return err
	}
	user.Password = string(hashedPassword)

	return s.repo.Create(user)
}

// =========================
// LOGIN LOGIC
// =========================
func (s *UserService) Login(email, password string) (*model.User, string, int64, error) {
	user, err := s.repo.FindByEmail(email)
	if err != nil || user == nil {
		return nil, "", 0, errors.New("invalid email or password")
	}

	// So sánh mật khẩu hash
	err = bcrypt.CompareHashAndPassword([]byte(user.Password), []byte(password))
	if err != nil {
		return nil, "", 0, errors.New("invalid email or password")
	}

	accessToken, expiresAt, err := s.generateAccessToken(user)
	if err != nil {
		return nil, "", 0, err
	}

	return user, accessToken, expiresAt.Unix(), nil
}

func (s *UserService) GetUserByID(id string) (*model.User, error) {
	return s.repo.FindByID(id)
}

func (s *UserService) GetUserRole(id string) (string, error) {
	return s.repo.FindRoleByID(id)
}

// =========================
// UPDATE USER
// =========================
func (s *UserService) UpdateUser(id, name, email string) error {
	return s.repo.Update(id, name, email)
}

// =========================
// DELETE USER
// =========================
func (s *UserService) DeleteUser(id string) error {
	return s.repo.SoftDelete(id)
}

// =========================
// USER EXISTS
// =========================
func (s *UserService) UserExists(id string) (bool, error) {
	return s.repo.Exists(id)
}

func isValidRole(role string) bool {
	switch role {
	case "CUSTOMER", "SELLER", "ADMIN":
		return true
	default:
		return false
	}
}

// =========================
// GET USER BY EMAIL (PUBLIC)
// =========================
func (s *UserService) GetUserByEmail(email string) (*model.User, error) {
	return s.repo.FindByEmailPublic(email)
}

// =========================
// EMAIL EXISTS
// =========================
func (s *UserService) EmailExists(email string) (bool, error) {
	return s.repo.EmailExists(email)
}

// =========================
// ACTIVATE USER
// =========================
func (s *UserService) ActivateUser(id string) error {
	return s.repo.Activate(id)
}

// =========================
// DEACTIVATE USER
// =========================
func (s *UserService) DeactivateUser(id string) error {
	return s.repo.Deactivate(id)
}

// =========================
// USER STATS
// =========================
func (s *UserService) UserStats() (map[string]any, error) {
	return s.repo.Stats()
}

// =========================
// VALIDATE USER (INTERNAL)
// =========================
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
