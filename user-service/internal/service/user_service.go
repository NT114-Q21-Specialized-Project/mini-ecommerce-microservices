package service

import (
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
	return s.repo.Create(user)
}
