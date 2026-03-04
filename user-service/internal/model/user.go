package model

import "time"

type User struct {
	ID                         string     `json:"id"`
	Name                       string     `json:"name"`
	Email                      string     `json:"email"`
	Password                   string     `json:"password,omitempty"`
	Role                       string     `json:"role"` // CUSTOMER, SELLER, ADMIN
	IsActive                   bool       `json:"is_active"`
	CreatedAt                  time.Time  `json:"created_at"`
	FailedLoginAttempts        int        `json:"-"`
	FailedLoginWindowStartedAt *time.Time `json:"-"`
	LockedUntil                *time.Time `json:"-"`
}

type RefreshToken struct {
	ID             string
	UserID         string
	TokenHash      string
	ExpiresAt      time.Time
	RevokedAt      *time.Time
	ReplacedByHash *string
	CreatedAt      time.Time
}
