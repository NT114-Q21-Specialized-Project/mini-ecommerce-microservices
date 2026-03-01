package repository

import (
	"database/sql"
	"errors"
	"fmt"
	"strings"

	"github.com/lib/pq"

	"user-service/internal/model"
)

var (
	ErrUserNotFound  = errors.New("user not found")
	ErrEmailConflict = errors.New("email already exists")
)

type ListUsersParams struct {
	Page      int
	PageSize  int
	Role      string
	Search    string
	SortBy    string
	SortOrder string
}

type ListUsersResult struct {
	Items []model.User
	Total int
}

type UserRepository struct {
	db *sql.DB
}

func NewUserRepository(db *sql.DB) *UserRepository {
	return &UserRepository{db: db}
}

func (r *UserRepository) ListActiveUsers(params ListUsersParams) (ListUsersResult, error) {
	filters := []string{"is_active = true"}
	args := []any{}
	argPos := 1

	if params.Role != "" {
		filters = append(filters, fmt.Sprintf("role = $%d", argPos))
		args = append(args, params.Role)
		argPos++
	}

	if params.Search != "" {
		filters = append(filters, fmt.Sprintf("(name ILIKE $%d OR email ILIKE $%d)", argPos, argPos))
		args = append(args, "%"+params.Search+"%")
		argPos++
	}

	whereClause := "WHERE " + strings.Join(filters, " AND ")

	var total int
	countQuery := fmt.Sprintf("SELECT COUNT(*) FROM users %s", whereClause)
	if err := r.db.QueryRow(countQuery, args...).Scan(&total); err != nil {
		return ListUsersResult{}, err
	}

	query := fmt.Sprintf(`
        SELECT id, name, email, role, is_active, created_at
        FROM users
        %s
        ORDER BY %s %s, id ASC
        LIMIT $%d OFFSET $%d
    `,
		whereClause,
		normalizeSortBy(params.SortBy),
		normalizeSortOrder(params.SortOrder),
		argPos,
		argPos+1,
	)

	offset := (params.Page - 1) * params.PageSize
	dataArgs := append(append([]any{}, args...), params.PageSize, offset)

	rows, err := r.db.Query(query, dataArgs...)
	if err != nil {
		return ListUsersResult{}, err
	}
	defer rows.Close()

	items := []model.User{}
	for rows.Next() {
		var user model.User
		if err := rows.Scan(
			&user.ID,
			&user.Name,
			&user.Email,
			&user.Role,
			&user.IsActive,
			&user.CreatedAt,
		); err != nil {
			return ListUsersResult{}, err
		}
		items = append(items, user)
	}

	if err := rows.Err(); err != nil {
		return ListUsersResult{}, err
	}

	return ListUsersResult{
		Items: items,
		Total: total,
	}, nil
}

func (r *UserRepository) Create(user *model.User) error {
	err := r.db.QueryRow(
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
	if err != nil {
		if isUniqueEmailError(err) {
			return ErrEmailConflict
		}
		return err
	}

	return nil
}

func (r *UserRepository) FindByEmail(email string) (*model.User, error) {
	var user model.User
	err := r.db.QueryRow(
		`
        SELECT id, name, email, password, role, is_active, created_at
        FROM users
        WHERE email = $1 AND is_active = true
        `,
		email,
	).Scan(
		&user.ID,
		&user.Name,
		&user.Email,
		&user.Password,
		&user.Role,
		&user.IsActive,
		&user.CreatedAt,
	)

	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}

	return &user, nil
}

func (r *UserRepository) FindByID(id string) (*model.User, error) {
	var user model.User
	err := r.db.QueryRow(
		`
        SELECT id, name, email, role, is_active, created_at
        FROM users
        WHERE id = $1 AND is_active = true
        `,
		id,
	).Scan(
		&user.ID,
		&user.Name,
		&user.Email,
		&user.Role,
		&user.IsActive,
		&user.CreatedAt,
	)

	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}

	return &user, nil
}

func (r *UserRepository) FindRoleByID(id string) (string, error) {
	var role string
	err := r.db.QueryRow(
		`SELECT role FROM users WHERE id = $1 AND is_active = true`,
		id,
	).Scan(&role)

	if errors.Is(err, sql.ErrNoRows) {
		return "", nil
	}
	if err != nil {
		return "", err
	}

	return role, nil
}

func (r *UserRepository) Update(id, name, email string) error {
	if name == "" && email == "" {
		return nil
	}

	query := "UPDATE users SET"
	args := []any{}
	argPos := 1

	if name != "" {
		query += fmt.Sprintf(" name = $%d,", argPos)
		args = append(args, name)
		argPos++
	}

	if email != "" {
		query += fmt.Sprintf(" email = $%d,", argPos)
		args = append(args, email)
		argPos++
	}

	query = strings.TrimSuffix(query, ",")
	query += fmt.Sprintf(" WHERE id = $%d AND is_active = true", argPos)
	args = append(args, id)

	result, err := r.db.Exec(query, args...)
	if err != nil {
		if isUniqueEmailError(err) {
			return ErrEmailConflict
		}
		return err
	}

	affected, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if affected == 0 {
		return ErrUserNotFound
	}

	return nil
}

func (r *UserRepository) SoftDelete(id string) error {
	result, err := r.db.Exec(`
        UPDATE users
        SET is_active = false
        WHERE id = $1 AND is_active = true
    `, id)
	if err != nil {
		return err
	}

	affected, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if affected == 0 {
		return ErrUserNotFound
	}

	return nil
}

func (r *UserRepository) Exists(id string) (bool, error) {
	var exists bool
	err := r.db.QueryRow(`
        SELECT EXISTS (
            SELECT 1 FROM users WHERE id = $1 AND is_active = true
        )
    `, id).Scan(&exists)

	return exists, err
}

func (r *UserRepository) FindByEmailPublic(email string) (*model.User, error) {
	var user model.User
	err := r.db.QueryRow(`
        SELECT id, name, email, role, is_active, created_at
        FROM users
        WHERE email = $1 AND is_active = true
    `, email).Scan(
		&user.ID,
		&user.Name,
		&user.Email,
		&user.Role,
		&user.IsActive,
		&user.CreatedAt,
	)

	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}

	return &user, nil
}

func (r *UserRepository) EmailExists(email string) (bool, error) {
	var exists bool
	err := r.db.QueryRow(`
        SELECT EXISTS (
            SELECT 1 FROM users WHERE email = $1
        )
    `, email).Scan(&exists)

	return exists, err
}

func (r *UserRepository) Activate(id string) error {
	result, err := r.db.Exec(`
        UPDATE users
        SET is_active = true
        WHERE id = $1
    `, id)
	if err != nil {
		return err
	}

	affected, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if affected == 0 {
		return ErrUserNotFound
	}

	return nil
}

func (r *UserRepository) Deactivate(id string) error {
	result, err := r.db.Exec(`
        UPDATE users
        SET is_active = false
        WHERE id = $1 AND is_active = true
    `, id)
	if err != nil {
		return err
	}

	affected, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if affected == 0 {
		return ErrUserNotFound
	}

	return nil
}

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

	if err := rows.Err(); err != nil {
		return nil, err
	}

	stats["by_role"] = roleStats
	return stats, nil
}

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

	if errors.Is(err, sql.ErrNoRows) {
		return false, "", false, nil
	}
	if err != nil {
		return false, "", false, err
	}

	return exists, role, active, nil
}

func normalizeSortBy(sortBy string) string {
	switch strings.ToLower(strings.TrimSpace(sortBy)) {
	case "name":
		return "name"
	case "email":
		return "email"
	case "role":
		return "role"
	default:
		return "created_at"
	}
}

func normalizeSortOrder(sortOrder string) string {
	switch strings.ToLower(strings.TrimSpace(sortOrder)) {
	case "asc":
		return "ASC"
	default:
		return "DESC"
	}
}

func isUniqueEmailError(err error) bool {
	var pqErr *pq.Error
	if !errors.As(err, &pqErr) {
		return false
	}

	return pqErr.Code == "23505"
}
