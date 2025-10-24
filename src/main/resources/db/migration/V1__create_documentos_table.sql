CREATE TABLE documentos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    filename VARCHAR(255) NOT NULL,
    filetype VARCHAR(255),
    resumo TEXT
);

