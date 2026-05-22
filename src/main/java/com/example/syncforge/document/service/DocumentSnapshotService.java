package com.example.syncforge.document.service;

import com.example.syncforge.document.entity.DocumentSnapshot;
import com.example.syncforge.document.entity.DocumentOperation;
import com.example.syncforge.document.mapper.DocumentOperationMapper;
import com.example.syncforge.document.mapper.DocumentSnapshotMapper;
import com.example.syncforge.realtime.ot.OtEditMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Service
@SuppressWarnings("unused")
public class DocumentSnapshotService {

    private static final int SNAPSHOT_INTERVAL = 200;

    private final DocumentSnapshotMapper documentSnapshotMapper;
    private final DocumentOperationMapper documentOperationMapper;
    private final TextOperationTransformer textOperationTransformer;

    public DocumentSnapshotService(DocumentSnapshotMapper documentSnapshotMapper,
                                   DocumentOperationMapper documentOperationMapper,
                                   TextOperationTransformer textOperationTransformer) {
        this.documentSnapshotMapper = documentSnapshotMapper;
        this.documentOperationMapper = documentOperationMapper;
        this.textOperationTransformer = textOperationTransformer;
    }

    /**
     * 按版本间隔创建快照：例如每 200 个版本存一次。
     * 在满足条件时，为文档的某个版本创建一份快照。
     */
    // Transactional 保证事物原子性
    @Transactional
    public void maybeCreateSnapshot(Long documentId,
                                    Long snapshotVersion,
                                    String title,
                                    String content,
                                    Long createdBy) {
        if (documentId == null || snapshotVersion == null || createdBy == null) {
            throw new IllegalArgumentException("documentId, snapshotVersion and createdBy are required");
        }
        if (snapshotVersion < 1) {
            return;
        }
        if (snapshotVersion % SNAPSHOT_INTERVAL != 0) {
            return;
        }
        if (documentSnapshotMapper.findByDocumentIdAndSnapshotVersion(documentId, snapshotVersion) != null) {
            return;
        }

        DocumentSnapshot snapshot = new DocumentSnapshot();
        snapshot.setDocumentId(documentId);
        snapshot.setSnapshotVersion(snapshotVersion);
        snapshot.setTitle(title);
        snapshot.setContent(content == null ? "" : content);
        snapshot.setContentHash(sha256(snapshot.getContent()));
        snapshot.setCreatedBy(createdBy);
        documentSnapshotMapper.insert(snapshot);
    }
    //获取某个版本 之前（不含等于）的最新一个快照。
    //@Transactional(readOnly = true) 标记为只读事务，可优化数据库连接、提升性能。
    @Transactional(readOnly = true)
    public DocumentSnapshot getLatestSnapshotBeforeVersion(Long documentId, Long snapshotVersion) {
        if (documentId == null || snapshotVersion == null || snapshotVersion < 0) {
            throw new IllegalArgumentException("documentId and snapshotVersion are required");
        }
        return documentSnapshotMapper.findLatestBeforeVersion(documentId, snapshotVersion);
    }

    /**
     * 使用最近快照 + 增量操作，重放出目标版本的内容。
     */
    @Transactional(readOnly = true)
    public String reconstructContent(Long documentId, Long targetVersion) {
        if (documentId == null || targetVersion == null || targetVersion < 0) {
            throw new IllegalArgumentException("documentId and targetVersion are required");
        }

        DocumentSnapshot snapshot = documentSnapshotMapper.findLatestBeforeVersion(documentId, targetVersion);
        String content = snapshot == null ? "" : safeContent(snapshot.getContent());
        // 获取快照版本
        long baseVersion = snapshot == null ? 0L : snapshot.getSnapshotVersion();
        // 根据快照版本重放
        List<DocumentOperation> operations = documentOperationMapper.findAfterVersion(documentId, baseVersion);
        for (DocumentOperation operation : operations) {
            if (operation.getServerVersion() != null && operation.getServerVersion() > targetVersion) {
                break;
            }
            // 应用后操作转换
            content = textOperationTransformer.apply(content, toEditMessage(operation));
        }
        // 返回得到最新的内容。
        return content;
    }
    // 编辑操作广播
    private OtEditMessage toEditMessage(DocumentOperation operation) {
        OtEditMessage message = new OtEditMessage();
        message.setClientOpId(operation.getClientOpId());
        message.setBaseVersion(operation.getBaseVersion());
        message.setOpType(operation.getOpType());
        message.setPosition(operation.getPosition());
        message.setContent(operation.getContent());
        message.setDeleteLength(operation.getDeleteLength());
        return message;
    }

    private String safeContent(String content) {
        return content == null ? "" : content;
    }
    //
    private String sha256(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}

