package com.example.syncforge.common;

public class UpdateContentRequest {
    private String content;
    private Long version;

    public UpdateContentRequest(){}
    public UpdateContentRequest(String content, Long version) {
        this.content = content;
        this.version = version;
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
}
