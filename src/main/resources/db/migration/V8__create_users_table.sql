CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_deleted_at ON users(deleted_at);

-- Criar usuário admin padrão
-- Senha: admin123 (BCrypt hash com força 10)
INSERT INTO users (username, email, password, role, enabled, created_at)
VALUES (
    'admin',
    'admin@llmontreal.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMye.IH8OJcZdlqOPT5Ct/EKcC9xPO6yN8q',
    'ADMIN',
    TRUE,
    NOW()
);
