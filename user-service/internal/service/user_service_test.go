package service

import (
	"errors"
	"fmt"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"golang.org/x/crypto/bcrypt"

	"user-service/internal/model"
	"user-service/internal/repository"
)

type fakeRefreshToken struct {
	userID    string
	expiresAt time.Time
	revokedAt *time.Time
}

type fakeRepo struct {
	usersByID      map[string]*model.User
	usersByEmail   map[string]string
	refreshByHash  map[string]*fakeRefreshToken
	nextIDSequence int
}

func newFakeRepo() *fakeRepo {
	return &fakeRepo{
		usersByID:     map[string]*model.User{},
		usersByEmail:  map[string]string{},
		refreshByHash: map[string]*fakeRefreshToken{},
	}
}

func (f *fakeRepo) ListActiveUsers(params repository.ListUsersParams) (repository.ListUsersResult, error) {
	items := make([]model.User, 0)
	for _, user := range f.usersByID {
		if user.IsActive {
			items = append(items, cloneUser(*user))
		}
	}
	return repository.ListUsersResult{Items: items, Total: len(items)}, nil
}

func (f *fakeRepo) Create(user *model.User) error {
	if user == nil {
		return errors.New("nil user")
	}
	if _, exists := f.usersByEmail[user.Email]; exists {
		return repository.ErrEmailConflict
	}

	f.nextIDSequence++
	copied := cloneUser(*user)
	if copied.ID == "" {
		copied.ID = fmt.Sprintf("u-test-%d", f.nextIDSequence)
	}
	copied.CreatedAt = time.Now().UTC()
	if !copied.IsActive {
		copied.IsActive = true
	}
	f.usersByID[copied.ID] = &copied
	f.usersByEmail[copied.Email] = copied.ID

	user.ID = copied.ID
	user.CreatedAt = copied.CreatedAt
	user.IsActive = copied.IsActive
	return nil
}

func (f *fakeRepo) FindByEmail(email string) (*model.User, error) {
	id, ok := f.usersByEmail[email]
	if !ok {
		return nil, nil
	}
	user, ok := f.usersByID[id]
	if !ok || !user.IsActive {
		return nil, nil
	}
	copied := cloneUser(*user)
	return &copied, nil
}

func (f *fakeRepo) FindByID(id string) (*model.User, error) {
	user, ok := f.usersByID[id]
	if !ok || !user.IsActive {
		return nil, nil
	}
	copied := cloneUser(*user)
	return &copied, nil
}

func (f *fakeRepo) FindRoleByID(id string) (string, error) {
	user, ok := f.usersByID[id]
	if !ok || !user.IsActive {
		return "", nil
	}
	return user.Role, nil
}

func (f *fakeRepo) Update(id, name, email string) error {
	user, ok := f.usersByID[id]
	if !ok || !user.IsActive {
		return repository.ErrUserNotFound
	}
	if email != "" {
		if existingID, exists := f.usersByEmail[email]; exists && existingID != id {
			return repository.ErrEmailConflict
		}
		delete(f.usersByEmail, user.Email)
		user.Email = email
		f.usersByEmail[email] = id
	}
	if name != "" {
		user.Name = name
	}
	return nil
}

func (f *fakeRepo) SoftDelete(id string) error {
	user, ok := f.usersByID[id]
	if !ok || !user.IsActive {
		return repository.ErrUserNotFound
	}
	user.IsActive = false
	return nil
}

func (f *fakeRepo) Exists(id string) (bool, error) {
	user, ok := f.usersByID[id]
	return ok && user.IsActive, nil
}

func (f *fakeRepo) FindByEmailPublic(email string) (*model.User, error) {
	return f.FindByEmail(email)
}

func (f *fakeRepo) FindByEmailAnyStatus(email string) (*model.User, error) {
	id, ok := f.usersByEmail[email]
	if !ok {
		return nil, nil
	}
	user, ok := f.usersByID[id]
	if !ok {
		return nil, nil
	}
	copied := cloneUser(*user)
	return &copied, nil
}

func (f *fakeRepo) EmailExists(email string) (bool, error) {
	_, ok := f.usersByEmail[email]
	return ok, nil
}

func (f *fakeRepo) Activate(id string) error {
	user, ok := f.usersByID[id]
	if !ok {
		return repository.ErrUserNotFound
	}
	user.IsActive = true
	return nil
}

func (f *fakeRepo) Deactivate(id string) error {
	user, ok := f.usersByID[id]
	if !ok || !user.IsActive {
		return repository.ErrUserNotFound
	}
	user.IsActive = false
	return nil
}

func (f *fakeRepo) Stats() (map[string]any, error) {
	return map[string]any{"total": len(f.usersByID)}, nil
}

func (f *fakeRepo) ValidateUser(id string) (bool, string, bool, error) {
	user, ok := f.usersByID[id]
	if !ok {
		return false, "", false, nil
	}
	return true, user.Role, user.IsActive, nil
}

func (f *fakeRepo) RegisterFailedLoginAttempt(userID string, now time.Time, window time.Duration, maxAttempts int, lockoutDuration time.Duration) (bool, error) {
	user, ok := f.usersByID[userID]
	if !ok {
		return false, repository.ErrUserNotFound
	}

	cutoff := now.Add(-window)
	if user.FailedLoginWindowStartedAt == nil || user.FailedLoginWindowStartedAt.Before(cutoff) {
		started := now
		user.FailedLoginWindowStartedAt = &started
		user.FailedLoginAttempts = 1
	} else {
		user.FailedLoginAttempts++
	}

	if user.FailedLoginAttempts >= maxAttempts {
		until := now.Add(lockoutDuration)
		user.LockedUntil = &until
		return true, nil
	}
	return false, nil
}

func (f *fakeRepo) ResetFailedLoginAttempts(userID string) error {
	user, ok := f.usersByID[userID]
	if !ok {
		return repository.ErrUserNotFound
	}
	user.FailedLoginAttempts = 0
	user.FailedLoginWindowStartedAt = nil
	user.LockedUntil = nil
	return nil
}

func (f *fakeRepo) StoreRefreshToken(userID, tokenHash string, expiresAt time.Time) error {
	f.refreshByHash[tokenHash] = &fakeRefreshToken{userID: userID, expiresAt: expiresAt}
	return nil
}

func (f *fakeRepo) RotateRefreshToken(oldTokenHash, newTokenHash string, newExpiresAt, now time.Time) (string, error) {
	oldToken, ok := f.refreshByHash[oldTokenHash]
	if !ok {
		return "", repository.ErrRefreshTokenNotFound
	}
	if oldToken.revokedAt != nil {
		return "", repository.ErrRefreshTokenRevoked
	}
	if !oldToken.expiresAt.After(now) {
		revoked := now
		oldToken.revokedAt = &revoked
		return "", repository.ErrRefreshTokenExpired
	}

	revoked := now
	oldToken.revokedAt = &revoked
	f.refreshByHash[newTokenHash] = &fakeRefreshToken{userID: oldToken.userID, expiresAt: newExpiresAt}
	return oldToken.userID, nil
}

func (f *fakeRepo) RevokeRefreshToken(tokenHash string, now time.Time) error {
	token, ok := f.refreshByHash[tokenHash]
	if !ok {
		return repository.ErrRefreshTokenNotFound
	}
	if token.revokedAt == nil {
		revoked := now
		token.revokedAt = &revoked
	}
	return nil
}

func cloneUser(user model.User) model.User {
	copied := user
	if user.FailedLoginWindowStartedAt != nil {
		t := *user.FailedLoginWindowStartedAt
		copied.FailedLoginWindowStartedAt = &t
	}
	if user.LockedUntil != nil {
		t := *user.LockedUntil
		copied.LockedUntil = &t
	}
	return copied
}

func seedUser(t *testing.T, repo *fakeRepo, name, email, password, role string) string {
	t.Helper()

	hashed, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		t.Fatalf("failed to hash password: %v", err)
	}

	user := &model.User{
		Name:     name,
		Email:    email,
		Password: string(hashed),
		Role:     role,
		IsActive: true,
	}
	if err := repo.Create(user); err != nil {
		t.Fatalf("failed to seed user: %v", err)
	}
	return user.ID
}

func TestCreateUserEnforcesPasswordPolicyAndHashesPassword(t *testing.T) {
	repo := newFakeRepo()
	svc := NewUserService(repo, "test-secret-value-with-minimum-32-bytes-long", 2*time.Hour, AuthPolicy{})

	weak := &model.User{Name: "A", Email: "weak@example.com", Password: "123456", Role: "CUSTOMER"}
	if err := svc.CreateUser(weak); err == nil {
		t.Fatalf("expected validation error for weak password")
	}

	strong := &model.User{Name: "Alice", Email: "alice@example.com", Password: "Strong@1234", Role: "CUSTOMER"}
	if err := svc.CreateUser(strong); err != nil {
		t.Fatalf("expected create user success, got error: %v", err)
	}

	stored, err := repo.FindByEmail("alice@example.com")
	if err != nil {
		t.Fatalf("find user failed: %v", err)
	}
	if stored == nil {
		t.Fatalf("expected user to exist after create")
	}
	if stored.Password == "Strong@1234" {
		t.Fatalf("password must be hashed")
	}
	if stored.Role != "CUSTOMER" {
		t.Fatalf("expected role CUSTOMER, got %s", stored.Role)
	}
}

func TestLoginIssuesAccessAndRefreshTokenWithMinimalClaims(t *testing.T) {
	repo := newFakeRepo()
	secret := "test-secret-value-with-minimum-32-bytes-long"
	svc := NewUserService(repo, secret, 2*time.Hour, AuthPolicy{})

	seedUser(t, repo, "Alice", "alice@example.com", "Strong@1234", "CUSTOMER")

	user, accessToken, _, refreshToken, _, err := svc.Login("alice@example.com", "Strong@1234")
	if err != nil {
		t.Fatalf("login failed: %v", err)
	}
	if user == nil {
		t.Fatalf("expected user in login response")
	}
	if accessToken == "" || refreshToken == "" {
		t.Fatalf("expected both access and refresh token")
	}

	parsed, err := jwt.Parse(accessToken, func(token *jwt.Token) (any, error) {
		return []byte(secret), nil
	})
	if err != nil || !parsed.Valid {
		t.Fatalf("invalid access token: %v", err)
	}

	claims, ok := parsed.Claims.(jwt.MapClaims)
	if !ok {
		t.Fatalf("unexpected claims type")
	}
	if claims["sub"] == nil || claims["role"] == nil {
		t.Fatalf("missing required claims sub/role")
	}
	if claims["email"] != nil || claims["name"] != nil {
		t.Fatalf("access token must not contain sensitive profile claims")
	}
}

func TestLoginLocksAccountAfterConsecutiveFailures(t *testing.T) {
	repo := newFakeRepo()
	policy := AuthPolicy{
		MaxFailedAttempts:   2,
		FailedAttemptWindow: 15 * time.Minute,
		LockoutDuration:     30 * time.Minute,
		RefreshTokenTTL:     24 * time.Hour,
	}
	svc := NewUserService(repo, "test-secret-value-with-minimum-32-bytes-long", 2*time.Hour, policy)
	now := time.Date(2026, 3, 4, 0, 0, 0, 0, time.UTC)
	svc.nowFn = func() time.Time { return now }

	seedUser(t, repo, "Bob", "bob@example.com", "Strong@1234", "CUSTOMER")

	if _, _, _, _, _, err := svc.Login("bob@example.com", "wrong-password"); !errors.Is(err, ErrInvalidCredentials) {
		t.Fatalf("expected ErrInvalidCredentials on first failed attempt, got %v", err)
	}

	if _, _, _, _, _, err := svc.Login("bob@example.com", "wrong-password"); !errors.Is(err, ErrAccountLocked) {
		t.Fatalf("expected ErrAccountLocked on threshold reached, got %v", err)
	}

	if _, _, _, _, _, err := svc.Login("bob@example.com", "Strong@1234"); !errors.Is(err, ErrAccountLocked) {
		t.Fatalf("expected ErrAccountLocked while lockout is active, got %v", err)
	}
}

func TestUpdateUserAndValidateUser(t *testing.T) {
	repo := newFakeRepo()
	svc := NewUserService(repo, "test-secret-value-with-minimum-32-bytes-long", 2*time.Hour, AuthPolicy{})

	firstID := seedUser(t, repo, "First", "first@example.com", "Strong@1234", "CUSTOMER")
	seedUser(t, repo, "Second", "second@example.com", "Strong@1234", "SELLER")

	if err := svc.UpdateUser(firstID, "", "second@example.com"); !errors.Is(err, ErrEmailAlreadyExists) {
		t.Fatalf("expected ErrEmailAlreadyExists, got %v", err)
	}

	if err := svc.UpdateUser(firstID, "First Updated", ""); err != nil {
		t.Fatalf("expected update success, got %v", err)
	}

	valid, role, active, err := svc.ValidateUser(firstID)
	if err != nil {
		t.Fatalf("validate user failed: %v", err)
	}
	if !valid || role != "CUSTOMER" || !active {
		t.Fatalf("unexpected validate user result: valid=%v role=%s active=%v", valid, role, active)
	}
}
