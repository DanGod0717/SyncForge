package com.example.syncforge.realtime;

import com.example.syncforge.document.entity.Document;
import com.example.syncforge.realtime.ot.OtAckMessage;
import com.example.syncforge.realtime.ot.OtServerEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class DocumentRealtimePublisher {
    //当文档更新时，把“更新事件”广播到 WebSocket 订阅频道 /topic/documents/{id}

    //Spring WebSocket 提供的 消息发送工具
    private final SimpMessagingTemplate messagingTemplate;

    public DocumentRealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }
    // 发送
    public void publishDocumentUpdated(Document document) {
        if (document == null || document.getId() == null) {
            return;
        }
        // 构造订阅路线
        String destination = "/topic/documents/" + document.getId();
        // 发送消息
        //{
        //  "type": "DOCUMENT_UPDATED",
        //  "documentId": 123,
        //  "version": 5,
        //  "lastEditUserId": 42,
        //  "updatedAt": "2026-04-13T10:30:00"
        //}

        //找到所有订阅 /topic/documents/123 的客户端
        //↓
        //逐个推送消息 前端接受消息
        messagingTemplate.convertAndSend(destination, DocumentRealtimeEvent.updated(document));
    }

    public void publishOtOperation(OtServerEvent event) {
        if (event == null || event.getDocumentId() == null) {
            return;
        }
        String destination = "/topic/documents/" + event.getDocumentId() + "/ops";
        messagingTemplate.convertAndSend(destination, event);
    }

    public void publishOtAck(Long userId, OtAckMessage ack) {
        if (userId == null || ack == null) {
            return;
        }
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/collab/ack", ack);
    }
}

