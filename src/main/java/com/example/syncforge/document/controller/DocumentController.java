package com.example.syncforge.document.controller;

import com.example.syncforge.common.ApiResponse;
import com.example.syncforge.common.UpdateContentRequest;
import com.example.syncforge.document.entity.Document;
import com.example.syncforge.document.service.DocumentService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/{id}")
    public ApiResponse<Document> getById(@PathVariable("id") Long id) {
        Document document = documentService.getById(id);
        if (document == null) {
            return ApiResponse.error(404, "Document not found");
        }
        return ApiResponse.success(document);
    }

    @PostMapping
    public ApiResponse<Document> create(@RequestBody Document document) {
        if (document.getOwnerUserId() == null || document.getTitle() == null || document.getTitle().trim().isEmpty()) {
            return ApiResponse.error(400, "ownerUserId and title are required");
        }
        System.out.println(document.toString());
        int affected = documentService.create(document);
        if (affected <= 0) {
            return ApiResponse.error(500, "Create document failed");
        }
        return ApiResponse.success(document);
    }

    @PutMapping("/{id}/content")
    public ApiResponse<Void> updateContent(@PathVariable("id") Long id,
                                           @RequestBody UpdateContentRequest request) {
        if (request == null || request.getVersion() == null) {
            return ApiResponse.error(400, "version is required");
        }
        boolean updated = documentService.updateContent(id, request.getContent(), request.getVersion());
        if (!updated) {
            return ApiResponse.error(409, "Document version conflict or document not found");
        }
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> softDelete(@PathVariable("id") Long id) {
        boolean deleted = documentService.softDelete(id);
        if (!deleted) {
            return ApiResponse.error(404, "Document not found or already deleted");
        }
        return ApiResponse.success(null);
    }

}