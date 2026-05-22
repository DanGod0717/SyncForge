USE syncforge;

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

