package model

import "time"

type User struct {
	ID        string    `json:"id"`
	Name      string    `json:"name"`
	Email     string    `json:"email"`
	Password  string    `json:"password,omitempty"`
	Role      string    `json:"role"`               // CUSTOMER, SELLER, ADMIN
	IsActive  bool      `json:"is_active"`
	CreatedAt time.Time `json:"created_at"`
}
