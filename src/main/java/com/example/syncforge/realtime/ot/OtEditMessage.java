package com.example.syncforge.realtime.ot;

public class OtEditMessage {
    //客户端操作ID 唯一标识一次编辑操作（客户端生成）
    //防止重复提交（幂等）
    //防止网络重发导致重复执行
    //用于 ACK 对应
    //服务器返回 ACK 时会带回这个 ID
    private String clientOpId;
    //客户端编辑时所基于的“文档版本”
    private Long baseVersion;
    //操作类型
    private String opType;
    //在文档中的位置（索引）
    private Integer position;
    //private String content;
    private String content;
    //删除操作使用
    private Integer deleteLength;

    public String getClientOpId() {
        return clientOpId;
    }

    public void setClientOpId(String clientOpId) {
        this.clientOpId = clientOpId;
    }

    public Long getBaseVersion() {
        return baseVersion;
    }

    public void setBaseVersion(Long baseVersion) {
        this.baseVersion = baseVersion;
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
}
