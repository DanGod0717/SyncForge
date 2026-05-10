package com.example.syncforge.document.entity;

import java.time.LocalDateTime;

public class Document {
    private Long id;
    private Long ownerUserId;
    private Long lastEditUserId;
    private String title;
    private String content;
    private Long version;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Document() {
    }

    public Document(Long id, Long ownerUserId, Long lastEditUserId, String title, String content, Long version,
                    Integer isDeleted, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.ownerUserId = ownerUserId;
        this.lastEditUserId = lastEditUserId;
        this.title = title;
        this.content = content;
        this.version = version;
        this.isDeleted = isDeleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public Long getLastEditUserId() {
        return lastEditUserId;
    }

    public void setLastEditUserId(Long lastEditUserId) {
        this.lastEditUserId = lastEditUserId;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Integer getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "Document{" +
                "id=" + id +
                ", ownerUserId=" + ownerUserId +
                ", lastEditUserId=" + lastEditUserId +
                ", title='" + title + '\'' +
                ", content='" + content + '\'' +
                ", version=" + version +
                ", isDeleted=" + isDeleted +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
