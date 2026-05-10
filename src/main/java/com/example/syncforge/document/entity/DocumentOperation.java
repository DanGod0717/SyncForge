package com.example.syncforge.document.entity;

import java.time.LocalDateTime;

public class DocumentOperation {
    private Long id;
    private Long documentId;
    private Long serverVersion;
    private Long baseVersion;
    private Long authorUserId;
    private String clientOpId;
    private String opType;
    private Integer position;
    private String content;
    private Integer deleteLength;
    private LocalDateTime createdAt;

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

    public Long getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(Long serverVersion) {
        this.serverVersion = serverVersion;
    }

    public Long getBaseVersion() {
        return baseVersion;
    }

    public void setBaseVersion(Long baseVersion) {
        this.baseVersion = baseVersion;
    }

    public Long getAuthorUserId() {
        return authorUserId;
    }

    public void setAuthorUserId(Long authorUserId) {
        this.authorUserId = authorUserId;
    }

    public String getClientOpId() {
        return clientOpId;
    }

    public void setClientOpId(String clientOpId) {
        this.clientOpId = clientOpId;
    }

    public String getOpType() {
        return opType;
    }

    public void setOpType(String opType) {
        this.opType = opType;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getDeleteLength() {
        return deleteLength;
    }

    public void setDeleteLength(Integer deleteLength) {
        this.deleteLength = deleteLength;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

