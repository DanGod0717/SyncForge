package com.example.syncforge.document.entity;

import java.time.LocalDateTime;
/**
 * 文档操作日志实体（OT Operation Log）。
 *
 * <p>一条记录表示“某个用户对某个文档做的一次原子编辑操作”：
 * - insert：在 position 位置插入 content
 * - delete：从 position 开始删除 deleteLength 个字符
 * - replace 就是两个操作合起来
 *
 * <p>该实体用于：
 * 1) OT 转换（基于历史操作做 transform）
 * 2) 幂等去重（clientOpId）
 * 3) 断线追帧（按 serverVersion 拉增量）
 */
public class DocumentOperation {
    private Long id;
    // 操作的文档id
    private Long documentId;
    // 该操作引用后得到的版本号
    private Long serverVersion;
    // 客户端发起操作时的版本号
    private Long baseVersion;
    // 操作发起人
    private Long authorUserId;
    /**
     * 客户端操作唯一ID（同一文档+同一用户内唯一）。
     * 用于网络重试幂等：重复提交同一 clientOpId 不应重复写入/升版本。
     */
    private String clientOpId;
    // 操作类型
    private String opType;
    // 操作位置 从0开始
    private Integer position;
    // 插入内容（仅 insert/replace 需要）
    private String content;
    // 删除长度 仅仅 删除需要 否则为0
    private Integer deleteLength;
    // 该操作的时间
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

