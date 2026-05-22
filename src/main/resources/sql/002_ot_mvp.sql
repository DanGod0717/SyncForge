USE syncforge;

CREATE TABLE IF NOT EXISTS document_operations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    server_version BIGINT NOT NULL,
    base_version BIGINT NOT NULL,
    author_user_id BIGINT NOT NULL,
    client_op_id VARCHAR(64) NOT NULL,
    op_type VARCHAR(16) NOT NULL,
    position INT NOT NULL,
    content LONGTEXT NULL,
    delete_length INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_server_version (document_id, server_version),
    UNIQUE KEY uk_doc_author_client_op (document_id, author_user_id, client_op_id),
    KEY idx_doc_ops_replay (document_id, created_at),
    CONSTRAINT fk_doc_ops_doc FOREIGN KEY (document_id) REFERENCES documents (id),
    CONSTRAINT fk_doc_ops_author FOREIGN KEY (author_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS document_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    snapshot_version BIGINT NOT NULL,
    title VARCHAR(255) NULL,
    content LONGTEXT NOT NULL,
    content_hash VARCHAR(64) NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_snapshot_doc_version (document_id, snapshot_version),
    KEY idx_snapshot_doc_version (document_id, snapshot_version DESC),
    CONSTRAINT fk_snapshot_doc FOREIGN KEY (document_id) REFERENCES documents (id),
    CONSTRAINT fk_snapshot_creator FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

