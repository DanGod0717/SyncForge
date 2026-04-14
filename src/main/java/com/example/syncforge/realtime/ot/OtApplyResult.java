package com.example.syncforge.realtime.ot;

public class OtApplyResult {
    private OtAckMessage ack;
    private OtServerEvent event;

    public OtAckMessage getAck() {
        return ack;
    }

    public void setAck(OtAckMessage ack) {
        this.ack = ack;
    }

    public OtServerEvent getEvent() {
        return event;
    }

    public void setEvent(OtServerEvent event) {
        this.event = event;
    }
}

