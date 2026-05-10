package com.example.syncforge.realtime.ot;

public class OtAckMessage {
    private String type;
    private Long documentId;
    private String clientOpId;
    private Long serverVersion;
    private boolean accepted;
    private String message;

    public static OtAckMessage accepted(Long documentId, String clientOpId, Long serverVersion) {
        OtAckMessage ack = new OtAckMessage();
        ack.setType("OT_ACK");
        ack.setDocumentId(documentId);
        ack.setClientOpId(clientOpId);
        ack.setServerVersion(serverVersion);
        ack.setAccepted(true);
        ack.setMessage("OK");
        return ack;
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

    public String getClientOpId() {
        return clientOpId;
    }

    public void setClientOpId(String clientOpId) {
        this.clientOpId = clientOpId;
    }

    public Long getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(Long serverVersion) {
        this.serverVersion = serverVersion;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
