-- Adicionar coluna user_id (nullable inicialmente)
ALTER TABLE documents
ADD COLUMN user_id BIGINT;

-- Atribuir documentos existentes ao usuário admin (id = 1)
UPDATE documents
SET user_id = 1
WHERE user_id IS NULL;

-- Tornar coluna NOT NULL
ALTER TABLE documents
ALTER COLUMN user_id SET NOT NULL;

-- Adicionar foreign key
ALTER TABLE documents
ADD CONSTRAINT fk_documents_user
    FOREIGN KEY (user_id) REFERENCES users(id);

-- Criar índice para performance
CREATE INDEX idx_documents_user_id ON documents(user_id);
