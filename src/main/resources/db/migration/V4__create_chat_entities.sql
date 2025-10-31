CREATE TABLE chat_session (
                              id BIGSERIAL PRIMARY KEY,
                              created_at TIMESTAMP NOT NULL,
                              updated_at TIMESTAMP,
                              is_active BOOLEAN,
                              document_id BIGINT UNIQUE,
                              CONSTRAINT fk_chat_session_document FOREIGN KEY (document_id) REFERENCES documents(id)
);

CREATE INDEX idx_chat_session_is_active ON chat_session(is_active);
CREATE INDEX idx_chat_session_created_at ON chat_session(created_at);

CREATE TABLE chat_message (
                              id BIGSERIAL PRIMARY KEY,
                              author VARCHAR(50) NOT NULL,
                              created_at TIMESTAMP NOT NULL,
                              message TEXT NOT NULL,
                              chat_session_id BIGINT NOT NULL,
                              CONSTRAINT fk_chat_message_session FOREIGN KEY (chat_session_id) REFERENCES chat_session(id)
);

CREATE INDEX idx_chat_message_session_id ON chat_message(chat_session_id);
CREATE INDEX idx_chat_message_created_at ON chat_message(created_at);
CREATE INDEX idx_chat_message_author ON chat_message(author);

ALTER TABLE documents
    ADD COLUMN chat_session_id BIGINT;

ALTER TABLE documents
    ADD CONSTRAINT fk_documents_chat_session
        FOREIGN KEY (chat_session_id) REFERENCES chat_session(id);
