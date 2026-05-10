package com.example.syncforge.realtime;

import com.example.syncforge.common.ApiResponse;
import com.example.syncforge.document.service.DocumentOtService;
import com.example.syncforge.realtime.ot.OtApplyResult;
import com.example.syncforge.realtime.ot.OtEditMessage;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class DocumentCollabController {

    private final DocumentOtService documentOtService;
    private final DocumentRealtimePublisher documentRealtimePublisher;

    public DocumentCollabController(DocumentOtService documentOtService,
                                    DocumentRealtimePublisher documentRealtimePublisher) {
        this.documentOtService = documentOtService;
        this.documentRealtimePublisher = documentRealtimePublisher;
    }
    //WebSocket STOMP 入口
    //OtEditMessage message,前端发送的 OT 操作消息，包含了操作类型（插入/删除）、位置、文本内容、基于哪个版本等信息
    // {
    //  "type": "insert",
    //  "pos": 5,
    //  "text": "hello"
    //}
    //当前 WebSocket 登录用户 Principal principal
    @MessageMapping("/documents/{id}/ops")
    public void applyOperation(@DestinationVariable("id") Long documentId,
                               OtEditMessage message,
                               Principal principal) {
        // 获取用户id如果没登录或者解析失败就拒绝操作，避免未授权用户进行 WebSocket 操作。这里的用户身份是通过前面提到的 StompAuthChannelInterceptor 在 WebSocket 握手和消息发送时绑定到 STOMP 会话上的，所以能保证安全性。
        //直接拒绝 WebSocket 操作
        Long userId = resolveUserId(principal);
        if (userId == null) {
            throw new IllegalArgumentException("Unauthorized websocket operation");
        }
        //收到客户端 OT 操作后调用 DocumentOtService 进行 OT 转换和应用，并通过 DocumentRealtimePublisher 把结果广播给所有订阅了这个文档的客户端
        OtApplyResult result = documentOtService.applyClientOperation(documentId, userId, message);
        documentRealtimePublisher.publishOtAck(userId, result.getAck());
        documentRealtimePublisher.publishOtOperation(result.getEvent());
    }

    @MessageExceptionHandler(IllegalArgumentException.class)
    @SendToUser("/queue/collab/errors")
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException ex) {
        return ApiResponse.error(400, ex.getMessage() == null ? "Bad request" : ex.getMessage());
    }

    private Long resolveUserId(Principal principal) {
        if (principal instanceof WsPrincipal) {
            return ((WsPrincipal) principal).getUserId();
        }
        if (principal == null || principal.getName() == null) {
            return null;
        }
        try {
            return Long.valueOf(principal.getName());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

