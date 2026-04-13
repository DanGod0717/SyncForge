package com.example.syncforge.document.service;

import com.example.syncforge.document.entity.Document;
import com.example.syncforge.document.entity.DocumentPermission;
import com.example.syncforge.document.mapper.DocumentMapper;
import com.example.syncforge.document.mapper.DocumentPermissionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final DocumentMapper documentMapper;
    // 文档权限设置
    private final DocumentPermissionMapper documentPermissionMapper;

    public DocumentService(DocumentMapper documentMapper, DocumentPermissionMapper documentPermissionMapper) {
        this.documentMapper = documentMapper;
        this.documentPermissionMapper = documentPermissionMapper;
    }

    public Document getById(Long id) {
        Document doc = documentMapper.findById(id);
        if (doc == null) {
            log.debug("Document not found, id={}", id);
        }
        return doc;
    }

    public int create(Document document, Long ownerUserId) {

        if (document.getVersion() == null) {
            document.setVersion(0L);
        }
        if (document.getIsDeleted() == null) {
            document.setIsDeleted(0);
        }
        document.setOwnerUserId(ownerUserId);
        document.setLastEditUserId(ownerUserId);
        int affected = documentMapper.insert(document);
        if (affected <= 0) {
            log.warn("Document insert affected no rows, ownerUserId={}, title={}", ownerUserId, document.getTitle());
        }
        return affected;
    }

    public boolean updateContent(Long id, String content, Long version, Long operatorUserId) {
        return documentMapper.updateContentByIdAndVersion(id, content, version, operatorUserId) > 0;
    }

    public boolean softDelete(Long id, Long operatorUserId) {
        return documentMapper.softDeleteById(id, operatorUserId) > 0;
    }

    public boolean canRead(Long userId, Document document) {
        if (document == null || userId == null) {
            return false;
        }
        if (userId.equals(document.getOwnerUserId())) {
            return true;
        }
        String permissionLevel = documentPermissionMapper.findPermissionLevelByDocumentIdAndUserId(document.getId(), userId);
        return "editor".equals(permissionLevel) || "viewer".equals(permissionLevel);
    }

    public boolean canEdit(Long userId, Document document) {
        if (document == null || userId == null) {
            return false;
        }
        if (userId.equals(document.getOwnerUserId())) {
            return true;
        }
        String permissionLevel = documentPermissionMapper.findPermissionLevelByDocumentIdAndUserId(document.getId(), userId);
        return "editor".equals(permissionLevel);
    }

    public boolean isOwner(Long userId, Document document) {
        return document != null && userId != null && userId.equals(document.getOwnerUserId());
    }

    public boolean grantPermission(Long documentId, Long targetUserId, String permissionLevel, Long grantedBy) {
        DocumentPermission permission = new DocumentPermission();
        permission.setDocumentId(documentId);
        permission.setUserId(targetUserId);
        permission.setPermissionLevel(permissionLevel);
        permission.setGrantedBy(grantedBy);
        return documentPermissionMapper.upsertPermission(permission) > 0;
    }

    public boolean revokePermission(Long documentId, Long targetUserId) {
        return documentPermissionMapper.softDeletePermission(documentId, targetUserId) > 0;
    }

    public List<Document> listMine(Long userId, Integer page, Integer size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        //偏移 第一页 (1-1)*safeSize ,第二页开始 (2-1)*safeSize 从这个开始
        int offset = (safePage - 1) * safeSize;
        // 开始与
        return documentMapper.findMineByUserId(userId, offset, safeSize);
    }

    public List<Document> listSharedWithMe(Long userId, Integer page, Integer size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        int offset = (safePage - 1) * safeSize;
        return documentMapper.findSharedWithMeByUserId(userId, offset, safeSize);
    }
    // 辅助方法 放置为null 使用默认值
    private int normalizePage(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}