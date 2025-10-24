ALTER TABLE documents
ADD COLUMN file_data BYTEA;

ALTER TABLE documents
RENAME COLUMN filetype TO file_type;

ALTER TABLE documents
RENAME COLUMN filename TO file_name;