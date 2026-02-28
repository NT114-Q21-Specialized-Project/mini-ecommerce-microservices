CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    password TEXT NOT NULL,
    role VARCHAR(20) DEFAULT 'CUSTOMER', -- ADMIN, SELLER, CUSTOMER
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Khởi tạo ngay 1 tài khoản Admin gốc (Mật khẩu: admin123)
-- Password đã được hash bằng bcrypt (cost 10)
INSERT INTO users (name, email, password, role, is_active)
VALUES (
    'Super Admin', 
    'admin@ems.com', 
    '$2a$10$OmJIqSDrey9VXzwNocVg4.UUw7HemEojckkc87jjJcwFr1mFQJWPe', 
    'ADMIN', 
    true
) ON CONFLICT (email) DO NOTHING;
