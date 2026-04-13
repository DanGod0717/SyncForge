package com.example.syncforge.document.controller;

import com.example.syncforge.auth.AuthContext;
import com.example.syncforge.common.ApiResponse;
import com.example.syncforge.common.PermissionRequest;
import com.example.syncforge.common.UpdateContentRequest;
import com.example.syncforge.document.entity.Document;
import com.example.syncforge.document.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

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
        boolean updated = documentService.updateContent(id, request.getContent(), request.getVersion(), userId);
        if (!updated) {
            return ApiResponse.error(409, "Document version conflict or document not found");
        }
        return ApiResponse.success(null);
    }

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

}