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
// CREATE USER
// =========================
func (r *UserRepository) Create(user *model.User) error {
    return r.db.QueryRow(
        `
        INSERT INTO users (name, email, role, is_active)
        VALUES ($1, $2, $3, true)
        RETURNING id, created_at, is_active
        `,
        user.Name,
        user.Email,
        user.Role,
    ).Scan(&user.ID, &user.CreatedAt, &user.IsActive)
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
    // Không có field nào để update
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

    // Xóa dấu phẩy cuối
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