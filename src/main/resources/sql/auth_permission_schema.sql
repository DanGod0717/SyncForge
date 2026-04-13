-- SyncForge schema init (legacy filename kept for compatibility)
-- If you need one-click initialization, run this file directly.

CREATE DATABASE IF NOT EXISTS syncforge
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE syncforge;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_user_id BIGINT NOT NULL,
    last_edit_user_id BIGINT NULL,
    title VARCHAR(255) NOT NULL,
    content LONGTEXT,
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_documents_owner (owner_user_id),
    KEY idx_documents_last_editor (last_edit_user_id),
    CONSTRAINT fk_documents_owner FOREIGN KEY (owner_user_id) REFERENCES users (id),
    CONSTRAINT fk_documents_last_editor FOREIGN KEY (last_edit_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS document_permissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    permission_level VARCHAR(16) NOT NULL,
    granted_by BIGINT NOT NULL,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_user (document_id, user_id),
    KEY idx_permission_user (user_id),
    KEY idx_permission_granted_by (granted_by),
    CONSTRAINT fk_permission_doc FOREIGN KEY (document_id) REFERENCES documents (id),
    CONSTRAINT fk_permission_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_permission_granted_by FOREIGN KEY (granted_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
