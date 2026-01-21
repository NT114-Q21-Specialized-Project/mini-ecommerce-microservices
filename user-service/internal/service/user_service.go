package service

import (
	"errors"

	"user-service/internal/model"
	"user-service/internal/repository"
)

type UserService struct {
	repo *repository.UserRepository
}

func NewUserService(repo *repository.UserRepository) *UserService {
	return &UserService{repo: repo}
}

func (s *UserService) GetUsers() ([]model.User, error) {
	return s.repo.FindAll()
}

func (s *UserService) CreateUser(user *model.User) error {

	if user.Role == "" {
		user.Role = "CUSTOMER"
	}

	if !isValidRole(user.Role) {
		return errors.New("invalid role")
	}

	return s.repo.Create(user)
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
	case "CUSTOMER", "SELLER":
		return true
	default:
		return false
	}
}