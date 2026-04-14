package com.example.syncforge.realtime;

import com.example.syncforge.document.entity.Document;

import java.time.LocalDateTime;

public class DocumentRealtimeEvent {
//实时协作系统里的 “文档更新事件 DTO（数据传输对象）”，专门用来把后端的 Document 状态 → 转成前端可消费的 WebSocket 消息。
    //DOCUMENT_UPDATED
    //DOCUMENT_DELETED
    //DOCUMENT_CREATED
    private String type;
    // 操作的文档id
    private Long documentId;
    // 当前版本号 协作核心
    private Long version;
    // 最后更新的用户
    private Long lastEditUserId;
    // 更新时间
    private LocalDateTime updatedAt;
    //更新方法
    public static DocumentRealtimeEvent updated(Document document) {
        DocumentRealtimeEvent event = new DocumentRealtimeEvent();
        event.setType("DOCUMENT_UPDATED");
        event.setDocumentId(document.getId());
        event.setVersion(document.getVersion());
        event.setLastEditUserId(document.getLastEditUserId());
        event.setUpdatedAt(document.getUpdatedAt());
        return event;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Long getLastEditUserId() {
        return lastEditUserId;
    }

    public void setLastEditUserId(Long lastEditUserId) {
        this.lastEditUserId = lastEditUserId;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

