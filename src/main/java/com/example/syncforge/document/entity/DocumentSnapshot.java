package com.example.syncforge.document.entity;

import java.time.LocalDateTime;

/**
 * 文档快照实体。
 *
 * <p>用于保存某一版本点上的完整文档状态，支持：
 * - 历史版本回溯
 * - 长历史增量回放加速
 * - 快速恢复最近状态
 */
public class DocumentSnapshot {

    private Long id;
    private Long documentId;
    private Long snapshotVersion;
    private String title;
    private String content;
    private String contentHash;
    private Long createdBy;
    private LocalDateTime createdAt;

    public DocumentSnapshot() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Long getSnapshotVersion() {
        return snapshotVersion;
    }

    public void setSnapshotVersion(Long snapshotVersion) {
        this.snapshotVersion = snapshotVersion;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "DocumentSnapshot{" +
                "id=" + id +
                ", documentId=" + documentId +
                ", snapshotVersion=" + snapshotVersion +
                ", title='" + title + '\'' +
                ", content='" + content + '\'' +
                ", contentHash='" + contentHash + '\'' +
                ", createdBy=" + createdBy +
                ", createdAt=" + createdAt +
                '}';
    }
}

