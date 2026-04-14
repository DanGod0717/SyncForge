package com.example.syncforge.realtime;

import com.example.syncforge.document.entity.Document;
import com.example.syncforge.document.service.DocumentService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {
// websocket 握手通过后，STOMP消息进入服务端再次做权限检查，补充握手拦截去 JwtHandshakeInterceptor 不能覆盖的场景，比如用户连接后被禁用，或者用户被移除文档访问权限等
    private static final Pattern DOCUMENT_TOPIC_PATTERN = Pattern.compile("^/topic/documents/(\\d+)(/ops)?$");
    private static final Pattern DOCUMENT_OP_SEND_PATTERN = Pattern.compile("^/app/documents/(\\d+)/ops$");

    private final DocumentService documentService;

    public StompAuthChannelInterceptor(DocumentService documentService) {
        this.documentService = documentService;
    }
    // 核心入口 提取发送用户和发送的目的地文档
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // 解析消息头，拿到STOMP命令和目的地
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        //不是标准 STOMP 命令就放过。
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        // 处理connect
        // 客户端建立STOMP会话时认证用户身份。
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            // 尝试拿到当前连接的用户id
            Long userId = resolveUserId(accessor);
            if (userId == null) {
                // 连接被拒绝
                throw new IllegalArgumentException("Unauthorized websocket connect");
            }
            // 把用户绑定到当前STOMP会话，后续消息可直接识别该用户
            accessor.setUser(new WsPrincipal(userId));
            return message;
        }
        // 处理 subscribe

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            Long userId = resolveUserId(accessor);
            if (userId == null) {
                // 拿不到用户id 就拒绝
                throw new IllegalArgumentException("Unauthorized websocket subscribe");
            }
            // 从订阅地址解析出文档id，验证用户是否有权限订阅这个文档的更新
            Long documentId = parseDocumentId(accessor.getDestination());
            if (documentId == null) {
                return message;
            }
            // 不存在则拒绝
            Document document = documentService.getById(documentId);
            if (document == null) {
                throw new IllegalArgumentException("Document not found");
            }
            // 查看当前用户有没有读权限 否则拒绝
            if (!documentService.canRead(userId, document)) {
                throw new IllegalArgumentException("Forbidden websocket subscribe");
            }
        }

        if (StompCommand.SEND.equals(accessor.getCommand())) {
            Long userId = resolveUserId(accessor);
            if (userId == null) {
                throw new IllegalArgumentException("Unauthorized websocket send");
            }
            Long documentId = parseDocumentIdFromSendDestination(accessor.getDestination());
            if (documentId == null) {
                return message;
            }
            Document document = documentService.getById(documentId);
            if (document == null) {
                throw new IllegalArgumentException("Document not found");
            }
            if (!documentService.canEdit(userId, document)) {
                throw new IllegalArgumentException("Forbidden websocket edit");
            }
        }

        return message;
    }
    //从 WebSocket 消息里提取：谁（userId） + 哪个文档（documentId）

    // 用户来源提取
    private Long resolveUserId(StompHeaderAccessor accessor) {
        // 从 WebSocket/STOMP 消息中 解析当前用户的 userId，STOMP 消息里绑定的“当前用户身份”
        Principal principal = accessor.getUser();
        if (principal instanceof WsPrincipal) {
            //如果是 WebSocket 自定义用户类型
            return ((WsPrincipal) principal).getUserId();
        }
        // 否则从会话属性取，WebSocket 握手阶段（HandshakeInterceptor）存进去的数据
        Map<String, Object> attributes = accessor.getSessionAttributes();
        // 说明用户信息没绑定成功，拒绝连接
        if (attributes == null) {
            return null;
        }
        //session.setAttribute("userId", xxx);
        Object userId = attributes.get(JwtHandshakeInterceptor.ATTR_USER_ID);
        if (userId instanceof Long) {
            return (Long) userId;
        }
        if (userId instanceof Number) {
            return ((Number) userId).longValue();
        }
        return null;
    }
    //从 STOMP 订阅/发送路径中 解析 documentId
    private Long parseDocumentId(String destination) {
        if (destination == null) {
            //防止空指针
            return null;
        }
        //用正则匹配 topic
        ///topic/document/123
        ///app/document/456
        Matcher matcher = DOCUMENT_TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            //表示这个消息不是 document 类型的，放行让后续处理器处理
            return null;
        }
        //正则第一个括号捕获的是 documentId
        return Long.valueOf(matcher.group(1));
    }

    private Long parseDocumentIdFromSendDestination(String destination) {
        if (destination == null) {
            return null;
        }
        Matcher matcher = DOCUMENT_OP_SEND_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            return null;
        }
        return Long.valueOf(matcher.group(1));
    }
}

