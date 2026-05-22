package com.example.syncforge.document.controller;

import com.example.syncforge.auth.AuthContext;
import com.example.syncforge.common.ApiResponse;
import com.example.syncforge.common.PermissionRequest;
import com.example.syncforge.common.UpdateContentRequest;
import com.example.syncforge.document.entity.Document;
import com.example.syncforge.document.entity.DocumentOperation;
import com.example.syncforge.document.entity.DocumentSnapshot;
import com.example.syncforge.realtime.DocumentRealtimePublisher;
import com.example.syncforge.document.service.DocumentOtService;
import com.example.syncforge.document.service.DocumentService;
import com.example.syncforge.document.service.DocumentSnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentService documentService;
    private final DocumentOtService documentOtService;
    private final DocumentSnapshotService documentSnapshotService;

    @Autowired(required = false)
    private DocumentRealtimePublisher documentRealtimePublisher;

    public DocumentController(DocumentService documentService,
                              DocumentOtService documentOtService,
                              DocumentSnapshotService documentSnapshotService) {
        this.documentService = documentService;
        this.documentOtService = documentOtService;
        this.documentSnapshotService = documentSnapshotService;
    }
    // 根据Userid 获取文档。
    @GetMapping("/{id}")
    public ApiResponse<Document> getById(@PathVariable("id") Long id) {
        Long userId = AuthContext.getUserId();
        Document document = documentService.getById(id);
        if (document == null) {
            return ApiResponse.error(404, "Document not found");
        }
        if (!documentService.canRead(userId, document)) {
            return ApiResponse.error(403, "No permission to read this document");
        }
        return ApiResponse.success(document);
    }

    // 断线重连时可直接跳转到最新状态（不回放历史 op）
    @GetMapping("/{id}/latest")
    public ApiResponse<Document> getLatestState(@PathVariable("id") Long id) {
        return getById(id);
    }
    // 创建文档
    @PostMapping
    public ApiResponse<Document> create(@RequestBody Document document) {
        Long userId = AuthContext.getUserId();

        if (document.getTitle() == null || document.getTitle().trim().isEmpty()) {
            return ApiResponse.error(400, "title is required");
        }

        int affected = documentService.create(document, userId);
        if (affected <= 0) {
            log.error("Create document failed, userId={}, title={}", userId, document.getTitle());
            return ApiResponse.error(500, "Create document failed");
        }
        log.info("Document created successfully, userId={}, documentId={}", userId, document.getId());
        return ApiResponse.success(document);
    }

    // 更新文档内容
    @PutMapping("/{id}/content")
    public ApiResponse<Void> updateContent(@PathVariable("id") Long id,
                                           @RequestBody UpdateContentRequest request) {
        Long userId = AuthContext.getUserId();
        if (request == null || request.getVersion() == null) {
            return ApiResponse.error(400, "version is required");
        }
        Document document = documentService.getById(id);
        if (document == null) {
            return ApiResponse.error(404, "Document not found");
        }
        if (!documentService.canEdit(userId, document)) {
            return ApiResponse.error(403, "No permission to edit this document");
        }
        // 更新是否成功
        boolean updated = documentService.updateContent(id, request.getContent(), request.getVersion(), userId);
        if (!updated) {
            return ApiResponse.error(409, "Document version conflict or document not found");
        }
        // 获取最新文档
        Document latest = documentService.getById(id);

        if (documentRealtimePublisher != null && latest != null) {
            // 更新后广发消息
            documentRealtimePublisher.publishDocumentUpdated(latest);
        }
        return ApiResponse.success(null);
    }
    // 软删除
    @DeleteMapping("/{id}")
    public ApiResponse<Void> softDelete(@PathVariable("id") Long id) {
        Long userId = AuthContext.getUserId();
        Document document = documentService.getById(id);
        if (document == null) {
            return ApiResponse.error(404, "Document not found or already deleted");
        }
        if (!documentService.isOwner(userId, document)) {
            return ApiResponse.error(403, "Only owner can delete document");
        }
        boolean deleted = documentService.softDelete(id, userId);
        if (!deleted) {
            return ApiResponse.error(404, "Document not found or already deleted");
        }
        return ApiResponse.success(null);
    }

    // 赋予 targetuserid 文档id的权限
    @PutMapping("/{id}/permissions/{targetUserId}")
    public ApiResponse<Void> grantPermission(@PathVariable("id") Long id,
                                             @PathVariable("targetUserId") Long targetUserId,
                                             @RequestBody PermissionRequest request) {
        Long userId = AuthContext.getUserId();
        Document document = documentService.getById(id);
        if (document == null) {
            return ApiResponse.error(404, "Document not found");
        }
        if (!documentService.isOwner(userId, document)) {
            return ApiResponse.error(403, "Only owner can grant permission");
        }
        if (request == null || request.getRole() == null) {
            return ApiResponse.error(400, "role is required");
        }
        String role = request.getRole().trim().toLowerCase();
        if (!"editor".equals(role) && !"viewer".equals(role)) {
            return ApiResponse.error(400, "role must be editor or viewer");
        }
        boolean ok = documentService.grantPermission(id, targetUserId, role, userId);
        if (!ok) {
            log.error("Grant permission failed, operatorUserId={}, documentId={}, targetUserId={}, role={}", userId, id, targetUserId, role);
            return ApiResponse.error(500, "Grant permission failed");
        }
        log.info("Permission granted, operatorUserId={}, documentId={}, targetUserId={}, role={}", userId, id, targetUserId, role);
        return ApiResponse.success(null);
    }

    // 删除targetUserId的文档id的权限
    @DeleteMapping("/{id}/permissions/{targetUserId}")
    public ApiResponse<Void> revokePermission(@PathVariable("id") Long id,
                                              @PathVariable("targetUserId") Long targetUserId) {
        Long userId = AuthContext.getUserId();
        Document document = documentService.getById(id);
        if (document == null) {
            return ApiResponse.error(404, "Document not found");
        }
        if (!documentService.isOwner(userId, document)) {
            return ApiResponse.error(403, "Only owner can revoke permission");
        }
        boolean ok = documentService.revokePermission(id, targetUserId);
        if (!ok) {
            return ApiResponse.error(404, "Permission not found");
        }
        return ApiResponse.success(null);
    }
    //page 默认 1，size 默认 20，最大 100
    @GetMapping("/mine")
    public ApiResponse<List<Document>> getMine(@RequestParam(value = "page", required = false) Integer page,
                                               @RequestParam(value = "size", required = false) Integer size) {
        // 页数和大小 可以不传这两个参数 但是会使用默认值，page =1  size =10
        Long userId = AuthContext.getUserId();
        // 根据userid展示 这个用户创建的文档列表 以及这个用户有权限访问的文档列表
        return ApiResponse.success(documentService.listMine(userId, page, size));
    }
    // 展示别人分享给我的
    @GetMapping("/shared-with-me")
    public ApiResponse<List<Document>> getSharedWithMe(@RequestParam(value = "page", required = false) Integer page,
                                                       @RequestParam(value = "size", required = false) Integer size) {
        Long userId = AuthContext.getUserId();
        return ApiResponse.success(documentService.listSharedWithMe(userId, page, size));
    }
    //   获取该版本之后的操作
    @GetMapping("/{id}/ops")
    public ApiResponse<List<DocumentOperation>> getOperationsAfterVersion(@PathVariable("id") Long id,
                                                                          @RequestParam("afterVersion") Long afterVersion,
                                                                          @RequestParam(value = "limit", required = false) Integer limit) {
        Long userId = AuthContext.getUserId();
        try {
            return ApiResponse.success(documentOtService.listOperationsAfterVersion(id, userId, afterVersion, limit));
        } catch (IllegalArgumentException ex) {
            String message = ex.getMessage() == null ? "Bad request" : ex.getMessage();
            if ("Document not found".equals(message)) {
                return ApiResponse.error(404, message);
            }
            if ("No permission to read this document".equals(message)) {
                return ApiResponse.error(403, message);
            }
            return ApiResponse.error(400, message);
        }
    }

    // 获取某版本之前最近的快照，便于历史回放或恢复基线定位
    @GetMapping("/{id}/snapshots/latest")
    public ApiResponse<DocumentSnapshot> getLatestSnapshot(@PathVariable("id") Long id,
                                                           @RequestParam("version") Long version) {
        Long userId = AuthContext.getUserId();
        Document document = documentService.getById(id);
        if (document == null) {
            return ApiResponse.error(404, "Document not found");
        }
        if (!documentService.canRead(userId, document)) {
            return ApiResponse.error(403, "No permission to read this document");
        }
        DocumentSnapshot snapshot = documentSnapshotService.getLatestSnapshotBeforeVersion(id, version);
        if (snapshot == null) {
            return ApiResponse.error(404, "Snapshot not found");
        }
        return ApiResponse.success(snapshot);
    }

    // 快照 + 增量操作重放，返回某个版本的完整内容
    @GetMapping("/{id}/reconstruct")
    public ApiResponse<String> reconstructContent(@PathVariable("id") Long id,
                                                  @RequestParam("version") Long version) {
        Long userId = AuthContext.getUserId();
        Document document = documentService.getById(id);
        if (document == null) {
            return ApiResponse.error(404, "Document not found");
        }
        if (!documentService.canRead(userId, document)) {
            return ApiResponse.error(403, "No permission to read this document");
        }
        return ApiResponse.success(documentSnapshotService.reconstructContent(id, version));
    }

}
