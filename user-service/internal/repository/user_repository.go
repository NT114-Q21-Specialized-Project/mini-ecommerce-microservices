package repository

import (
	"database/sql"
	"fmt"
	"strings"

	"user-service/internal/model"
)

type UserRepository struct {
	db *sql.DB
}

func NewUserRepository(db *sql.DB) *UserRepository {
	return &UserRepository{db: db}
}

// =========================
// FIND ALL USERS
// =========================
func (r *UserRepository) FindAll() ([]model.User, error) {
	rows, err := r.db.Query(`
        SELECT id, name, email, role, is_active, created_at
        FROM users
        WHERE is_active = true
    `)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	users := []model.User{}

	for rows.Next() {
		var u model.User
		if err := rows.Scan(
			&u.ID,
			&u.Name,
			&u.Email,
			&u.Role,
			&u.IsActive,
			&u.CreatedAt,
		); err != nil {
			return nil, err
		}
		users = append(users, u)
	}

	return users, nil
}

// =========================
// CREATE USER (UPDATED TO INCLUDE PASSWORD)
// =========================
func (r *UserRepository) Create(user *model.User) error {
	return r.db.QueryRow(
		`
        INSERT INTO users (name, email, password, role, is_active)
        VALUES ($1, $2, $3, $4, true)
        RETURNING id, created_at, is_active
        `,
		user.Name,
		user.Email,
		user.Password,
		user.Role,
	).Scan(&user.ID, &user.CreatedAt, &user.IsActive)
}

// =========================
// FIND BY EMAIL (NEW FOR LOGIN)
// =========================
func (r *UserRepository) FindByEmail(email string) (*model.User, error) {
	var u model.User
	err := r.db.QueryRow(
		`
        SELECT id, name, email, password, role, is_active, created_at
        FROM users
        WHERE email = $1 AND is_active = true
        `,
		email,
	).Scan(
		&u.ID,
		&u.Name,
		&u.Email,
		&u.Password,
		&u.Role,
		&u.IsActive,
		&u.CreatedAt,
	)

	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &u, nil
}

// =========================
// FIND USER BY ID
// =========================
func (r *UserRepository) FindByID(id string) (*model.User, error) {
	var u model.User

	err := r.db.QueryRow(
		`
        SELECT id, name, email, role, is_active, created_at
        FROM users
        WHERE id = $1 AND is_active = true
        `,
		id,
	).Scan(
		&u.ID,
		&u.Name,
		&u.Email,
		&u.Role,
		&u.IsActive,
		&u.CreatedAt,
	)

	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}

	return &u, nil
}

// =========================
// FIND USER ROLE
// =========================
func (r *UserRepository) FindRoleByID(id string) (string, error) {
	var role string

	err := r.db.QueryRow(
		`SELECT role FROM users WHERE id = $1 AND is_active = true`,
		id,
	).Scan(&role)

	if err == sql.ErrNoRows {
		return "", nil
	}
	if err != nil {
		return "", err
	}

	return role, nil
}

// =========================
// UPDATE USER (PARTIAL UPDATE)
// =========================
func (r *UserRepository) Update(id, name, email string) error {
	if name == "" && email == "" {
		return nil
	}

	query := `UPDATE users SET`
	args := []any{}
	i := 1

	if name != "" {
		query += fmt.Sprintf(" name = $%d,", i)
		args = append(args, name)
		i++
	}

	if email != "" {
		query += fmt.Sprintf(" email = $%d,", i)
		args = append(args, email)
		i++
	}

	query = strings.TrimSuffix(query, ",")

	query += fmt.Sprintf(" WHERE id = $%d AND is_active = true", i)
	args = append(args, id)

	_, err := r.db.Exec(query, args...)
	return err
}

// =========================
// SOFT DELETE USER
// =========================
func (r *UserRepository) SoftDelete(id string) error {
	_, err := r.db.Exec(`
        UPDATE users
        SET is_active = false
        WHERE id = $1
    `, id)

	return err
}

// =========================
// USER EXISTS
// =========================
func (r *UserRepository) Exists(id string) (bool, error) {
	var exists bool

	err := r.db.QueryRow(`
        SELECT EXISTS (
            SELECT 1 FROM users WHERE id = $1 AND is_active = true
        )
    `, id).Scan(&exists)

	return exists, err
}

// =========================
// FIND USER BY EMAIL (PUBLIC - NO PASSWORD)
// =========================
func (r *UserRepository) FindByEmailPublic(email string) (*model.User, error) {
	var u model.User

	err := r.db.QueryRow(`
        SELECT id, name, email, role, is_active, created_at
        FROM users
        WHERE email = $1 AND is_active = true
    `, email).Scan(
		&u.ID,
		&u.Name,
		&u.Email,
		&u.Role,
		&u.IsActive,
		&u.CreatedAt,
	)

	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}

	return &u, nil
}

// =========================
// CHECK EMAIL EXISTS
// =========================
func (r *UserRepository) EmailExists(email string) (bool, error) {
	var exists bool

	err := r.db.QueryRow(`
        SELECT EXISTS (
            SELECT 1 FROM users WHERE email = $1
        )
    `, email).Scan(&exists)

	return exists, err
}

// =========================
// ACTIVATE USER
// =========================
func (r *UserRepository) Activate(id string) error {
	_, err := r.db.Exec(`
        UPDATE users
        SET is_active = true
        WHERE id = $1
    `, id)

	return err
}

// =========================
// DEACTIVATE USER
// =========================
func (r *UserRepository) Deactivate(id string) error {
	_, err := r.db.Exec(`
        UPDATE users
        SET is_active = false
        WHERE id = $1
    `, id)

	return err
}

// =========================
// USER STATS
// =========================
func (r *UserRepository) Stats() (map[string]any, error) {
	stats := make(map[string]any)

	var total int
	var active int
	var inactive int

	err := r.db.QueryRow(`
        SELECT
            COUNT(*) AS total,
            COUNT(*) FILTER (WHERE is_active = true) AS active,
            COUNT(*) FILTER (WHERE is_active = false) AS inactive
        FROM users
    `).Scan(&total, &active, &inactive)

	if err != nil {
		return nil, err
	}

	stats["total"] = total
	stats["active"] = active
	stats["inactive"] = inactive

	rows, err := r.db.Query(`
        SELECT role, COUNT(*) 
        FROM users
        GROUP BY role
    `)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	roleStats := make(map[string]int)
	for rows.Next() {
		var role string
		var count int
		if err := rows.Scan(&role, &count); err != nil {
			return nil, err
		}
		roleStats[role] = count
	}

	stats["by_role"] = roleStats
	return stats, nil
}

// =========================
// VALIDATE USER (INTERNAL)
// =========================
func (r *UserRepository) ValidateUser(id string) (bool, string, bool, error) {
	var exists bool
	var role string
	var active bool

	err := r.db.QueryRow(`
        SELECT 
            EXISTS (SELECT 1 FROM users WHERE id = $1),
            COALESCE(role, ''),
            COALESCE(is_active, false)
        FROM users
        WHERE id = $1
    `, id).Scan(&exists, &role, &active)

	if err == sql.ErrNoRows {
		return false, "", false, nil
	}
	if err != nil {
		return false, "", false, err
	}

	return exists, role, active, nil
}
