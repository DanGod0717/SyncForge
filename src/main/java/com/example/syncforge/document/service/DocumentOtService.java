package com.example.syncforge.document.service;

import com.example.syncforge.document.entity.Document;
import com.example.syncforge.document.entity.DocumentOperation;
import com.example.syncforge.document.mapper.DocumentMapper;
import com.example.syncforge.document.mapper.DocumentOperationMapper;
import com.example.syncforge.realtime.ot.OtAckMessage;
import com.example.syncforge.realtime.ot.OtApplyResult;
import com.example.syncforge.realtime.ot.OtEditMessage;
import com.example.syncforge.realtime.ot.OtServerEvent;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class DocumentOtService {
//接收用户编辑操作 → OT冲突转换 → 更新文档 → 持久化操作历史 → 生成广播事件
    private static final int DEFAULT_CATCH_UP_LIMIT = 200;
    private static final int MAX_CATCH_UP_LIMIT = 1000;

    private final DocumentService documentService;
    private final DocumentMapper documentMapper;
    private final DocumentOperationMapper documentOperationMapper;
    private final DocumentSnapshotService documentSnapshotService;
    private final TextOperationTransformer textOperationTransformer;

    public DocumentOtService(DocumentService documentService,
                             DocumentMapper documentMapper,
                             DocumentOperationMapper documentOperationMapper,
                             DocumentSnapshotService documentSnapshotService,
                             TextOperationTransformer textOperationTransformer) {
        this.documentService = documentService;
        this.documentMapper = documentMapper;
        this.documentOperationMapper = documentOperationMapper;
        this.documentSnapshotService = documentSnapshotService;
        this.textOperationTransformer = textOperationTransformer;
    }
    // 核心方法
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public OtApplyResult applyClientOperation(Long documentId, Long userId, OtEditMessage message) {
        //参数校验 保证合法
        validateMessage(message);
        // 幂等短路，如果已经处理则返回ACK
        DocumentOperation existingOperation = documentOperationMapper.findByClientOp(documentId, userId, message.getClientOpId());
        if (existingOperation != null) {
            return buildIdempotentAckResult(documentId, message.getClientOpId(), existingOperation.getServerVersion());
        }

        // 获取文档并加行锁，让同一文档的并发编辑按到达顺序串行处理 同文档并发按到达处理
        // 在事务中锁定 documents 表中符合条件的行，防止其他事务修改或删除它们，直到当前事务结束。
        Document document = documentMapper.findByIdForUpdate(documentId);
        if (document == null) {
            throw new IllegalArgumentException("Document not found");
        }
        // 查看权限
        if (!documentService.canEdit(userId, document)) {
            throw new IllegalArgumentException("No permission to edit this document");
        }
        // 版本检查
        long currentVersion = document.getVersion() == null ? 0L : document.getVersion();
        if (message.getBaseVersion() > currentVersion) {
            //TODO 直接回滚到currentVersion 然后更新OT，更新文档
            throw new IllegalArgumentException("baseVersion is ahead of server version");
        }
        // 客户端版本之后发生的所有操作都需要拿来做 OT 转换，保证操作能正确应用到当前版本的文档内容上
        List<DocumentOperation> history = documentOperationMapper.findAfterVersion(documentId, message.getBaseVersion());
        // OT转换 核心客户端旧操作转为最新版本操作 移动操作的位置
        OtEditMessage transformed = textOperationTransformer.transform(message, history);
        // 检查文本内容
        String currentContent = document.getContent() == null ? "" : document.getContent();
        validateOperationRange(transformed, currentContent.length());
        // 应用到内容
        String nextContent = textOperationTransformer.apply(currentContent, transformed);
        int updated = documentMapper.updateContentByIdAndVersion(documentId, nextContent, currentVersion, userId);
        if (updated <= 0) {
            throw new IllegalStateException("Concurrent update detected");
        }
        //写入操作历史 当前操作写入
        long nextVersion = currentVersion + 1;
        DocumentOperation operation = new DocumentOperation();
        operation.setDocumentId(documentId);
        operation.setServerVersion(nextVersion);
        operation.setBaseVersion(message.getBaseVersion());
        operation.setAuthorUserId(userId);
        operation.setClientOpId(message.getClientOpId());
        operation.setOpType(transformed.getOpType());
        operation.setPosition(transformed.getPosition());
        operation.setContent(transformed.getContent());
        operation.setDeleteLength(transformed.getDeleteLength());
        try {
            documentOperationMapper.insert(operation);
        } catch (DuplicateKeyException ex) {
            DocumentOperation duplicated = documentOperationMapper.findByClientOp(documentId, userId, message.getClientOpId());
            if (duplicated != null) {
                return buildIdempotentAckResult(documentId, message.getClientOpId(), duplicated.getServerVersion());
            }
            throw new IllegalArgumentException("Duplicate clientOpId for this document and user");
        }

        documentSnapshotService.maybeCreateSnapshot(documentId, nextVersion, document.getTitle(), nextContent, userId);

        // 构建广播通知通知所有协作者 UI更新
        OtServerEvent event = new OtServerEvent();
        event.setType("OT_APPLIED");
        event.setDocumentId(documentId);
        event.setServerVersion(nextVersion);
        event.setAuthorUserId(userId);
        event.setClientOpId(message.getClientOpId());
        event.setOpType(transformed.getOpType());
        event.setPosition(transformed.getPosition());
        event.setContent(transformed.getContent());
        event.setDeleteLength(transformed.getDeleteLength());
        event.setUpdatedAt(LocalDateTime.now());
        // 返回结果
        OtApplyResult result = new OtApplyResult();
        //ACK（给操作人）
        result.setAck(OtAckMessage.accepted(documentId, message.getClientOpId(), nextVersion));
        //广播
        result.setEvent(event);
        // 返回给上层广播
        return result;
    }

    private OtApplyResult buildIdempotentAckResult(Long documentId, String clientOpId, Long serverVersion) {
        OtApplyResult result = new OtApplyResult();
        result.setAck(OtAckMessage.accepted(documentId, clientOpId, serverVersion));
        return result;
    }
    // 客户端在收到 ACK 后会知道自己的操作被接受了，并且可以把这个操作加入到本地的已确认操作列表中。
    // 对于其他协作者来说，广播事件会通知他们有新的操作被应用了，他们需要根据这个事件来更新自己的文档内容和版本号。
    public List<DocumentOperation> listOperationsAfterVersion(Long documentId, Long userId, Long afterVersion, Integer limit) {
        if (afterVersion == null || afterVersion < 0) {
            throw new IllegalArgumentException("afterVersion is required and must be >= 0");
        }
        int safeLimit = normalizeCatchUpLimit(limit);
        Document document = documentService.getById(documentId);
        if (document == null) {
            throw new IllegalArgumentException("Document not found");
        }
        if (!documentService.canRead(userId, document)) {
            throw new IllegalArgumentException("No permission to read this document");
        }
        return documentOperationMapper.findAfterVersionWithLimit(documentId, afterVersion, safeLimit);
    }

    private int normalizeCatchUpLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_CATCH_UP_LIMIT;
        }
        if (limit < 1 || limit > MAX_CATCH_UP_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_CATCH_UP_LIMIT);
        }
        return limit;
    }

    private void validateMessage(OtEditMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("operation body is required");
        }
        if (message.getBaseVersion() == null || message.getBaseVersion() < 0) {
            throw new IllegalArgumentException("baseVersion is required and must be >= 0");
        }
        if (message.getPosition() == null || message.getPosition() < 0) {
            throw new IllegalArgumentException("position is required and must be >= 0");
        }
        if (!StringUtils.hasText(message.getClientOpId())) {
            throw new IllegalArgumentException("clientOpId is required");
        }

        String opType = message.getOpType() == null ? "" : message.getOpType().trim().toLowerCase(Locale.ROOT);
        message.setOpType(opType);
        if (!"insert".equals(opType) && !"delete".equals(opType)) {
            throw new IllegalArgumentException("opType must be insert or delete");
        }
        if ("insert".equals(opType)) {
            if (message.getContent() == null) {
                throw new IllegalArgumentException("content is required for insert");
            }
            message.setDeleteLength(0);
        } else {
            if (message.getDeleteLength() == null || message.getDeleteLength() <= 0) {
                throw new IllegalArgumentException("deleteLength is required for delete");
            }
            message.setContent(null);
        }
    }
    // 检查操作范围
    private void validateOperationRange(OtEditMessage message, int contentLength) {
        int position = message.getPosition();
        if (position > contentLength) {
            throw new IllegalArgumentException("position out of range");
        }
        if ("delete".equals(message.getOpType())) {
            int deleteLength = message.getDeleteLength();
            if (position + deleteLength > contentLength) {
                throw new IllegalArgumentException("delete range out of content");
            }
        }
    }
}

