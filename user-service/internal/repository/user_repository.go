package repository

import (
	"database/sql"

	"user-service/internal/model"
)

type UserRepository struct {
	db *sql.DB
}

func NewUserRepository(db *sql.DB) *UserRepository {
	return &UserRepository{db: db}
}

func (r *UserRepository) FindAll() ([]model.User, error) {
	rows, err := r.db.Query(`SELECT id, name, email, created_at FROM users`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	users := []model.User{}
	for rows.Next() {
		var u model.User
		if err := rows.Scan(&u.ID, &u.Name, &u.Email, &u.CreatedAt); err != nil {
			return nil, err
		}
		users = append(users, u)
	}
	return users, nil
}

func (r *UserRepository) Create(user *model.User) error {
	return r.db.QueryRow(
		`INSERT INTO users (name, email) VALUES ($1, $2)
		 RETURNING id, created_at`,
		user.Name,
		user.Email,
	).Scan(&user.ID, &user.CreatedAt)
}

func (r *UserRepository) FindByID(id string) (*model.User, error) {
	var u model.User

	err := r.db.QueryRow(
		`SELECT id, name, email, created_at FROM users WHERE id = $1`,
		id,
	).Scan(&u.ID, &u.Name, &u.Email, &u.CreatedAt)

	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}

	return &u, nil
}
