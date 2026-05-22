package com.example.syncforge.document.controller;

import com.example.syncforge.auth.AuthContext;
import com.example.syncforge.common.ApiResponse;
import com.example.syncforge.common.PermissionRequest;
import com.example.syncforge.common.UpdateContentRequest;
import com.example.syncforge.document.entity.Document;
import com.example.syncforge.document.entity.DocumentOperation;
import com.example.syncforge.document.entity.DocumentSnapshot;
import com.example.syncforge.document.service.DocumentOtService;
import com.example.syncforge.document.service.DocumentService;
import com.example.syncforge.document.service.DocumentSnapshotService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {

    @Mock
    private DocumentService documentService;

    @Mock
    private DocumentOtService documentOtService;

    @Mock
    private DocumentSnapshotService documentSnapshotService;

    @InjectMocks
    private DocumentController documentController;

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void getByIdShouldReturn404WhenDocumentNotFound() {
        AuthContext.setUserId(1L);
        when(documentService.getById(99L)).thenReturn(null);

        ApiResponse<Document> response = documentController.getById(99L);

        assertEquals(404, response.getCode());
    }

    @Test
    void getByIdShouldReturn403WhenUserHasNoReadPermission() {
        AuthContext.setUserId(3L);
        Document doc = buildDoc(10L, 2L);
        when(documentService.getById(10L)).thenReturn(doc);
        when(documentService.canRead(3L, doc)).thenReturn(false);

        ApiResponse<Document> response = documentController.getById(10L);

        assertEquals(403, response.getCode());
    }

    @Test
    void createShouldReturn500WhenInsertFails() {
        AuthContext.setUserId(1L);
        Document input = new Document();
        input.setTitle("new doc");

        when(documentService.create(input, 1L)).thenReturn(0);

        ApiResponse<Document> response = documentController.create(input);

        assertEquals(500, response.getCode());
    }

    @Test
    void updateContentShouldReturn409WhenVersionConflict() {
        AuthContext.setUserId(1L);
        Document doc = buildDoc(20L, 1L);
        UpdateContentRequest request = new UpdateContentRequest("v2", 1L);

        when(documentService.getById(20L)).thenReturn(doc);
        when(documentService.canEdit(1L, doc)).thenReturn(true);
        when(documentService.updateContent(20L, "v2", 1L, 1L)).thenReturn(false);

        ApiResponse<Void> response = documentController.updateContent(20L, request);

        assertEquals(409, response.getCode());
    }

    @Test
    void grantPermissionShouldReturn403WhenCallerIsNotOwner() {
        AuthContext.setUserId(2L);
        Document doc = buildDoc(30L, 1L);
        PermissionRequest request = new PermissionRequest();
        request.setRole("editor");

        when(documentService.getById(30L)).thenReturn(doc);
        when(documentService.isOwner(2L, doc)).thenReturn(false);

        ApiResponse<Void> response = documentController.grantPermission(30L, 8L, request);

        assertEquals(403, response.getCode());
    }

    @Test
    void grantPermissionShouldReturn500WhenServiceFails() {
        AuthContext.setUserId(1L);
        Document doc = buildDoc(31L, 1L);
        PermissionRequest request = new PermissionRequest();
        request.setRole("viewer");

        when(documentService.getById(31L)).thenReturn(doc);
        when(documentService.isOwner(1L, doc)).thenReturn(true);
        when(documentService.grantPermission(31L, 9L, "viewer", 1L)).thenReturn(false);

        ApiResponse<Void> response = documentController.grantPermission(31L, 9L, request);

        assertEquals(500, response.getCode());
    }

    @Test
    void revokePermissionShouldReturn403WhenCallerIsNotOwner() {
        AuthContext.setUserId(2L);
        Document doc = buildDoc(40L, 1L);

        when(documentService.getById(40L)).thenReturn(doc);
        when(documentService.isOwner(2L, doc)).thenReturn(false);

        ApiResponse<Void> response = documentController.revokePermission(40L, 9L);

        assertEquals(403, response.getCode());
    }

    @Test
    void softDeleteShouldReturn200ForOwner() {
        AuthContext.setUserId(1L);
        Document doc = buildDoc(50L, 1L);

        when(documentService.getById(50L)).thenReturn(doc);
        when(documentService.isOwner(1L, doc)).thenReturn(true);
        when(documentService.softDelete(50L, 1L)).thenReturn(true);

        ApiResponse<Void> response = documentController.softDelete(50L);

        assertEquals(200, response.getCode());
        assertNotNull(response);
    }

    @Test
    void getMineShouldReturnCurrentUserDocuments() {
        AuthContext.setUserId(7L);
        List<Document> documents = Arrays.asList(buildDoc(100L, 7L), buildDoc(101L, 7L));
        when(documentService.listMine(7L, 1, 10)).thenReturn(documents);

        ApiResponse<List<Document>> response = documentController.getMine(1, 10);

        assertEquals(200, response.getCode());
        assertNotNull(response.getData());
        assertEquals(2, response.getData().size());
    }

    @Test
    void getSharedWithMeShouldReturnAccessibleDocuments() {
        AuthContext.setUserId(8L);
        List<Document> documents = Collections.singletonList(buildDoc(200L, 1L));
        when(documentService.listSharedWithMe(8L, null, null)).thenReturn(documents);

        ApiResponse<List<Document>> response = documentController.getSharedWithMe(null, null);

        assertEquals(200, response.getCode());
        assertNotNull(response.getData());
        assertEquals(Long.valueOf(200L), response.getData().get(0).getId());
    }

    @Test
    void getOperationsAfterVersionShouldReturn400ForNegativeVersion() {
        AuthContext.setUserId(1L);
        when(documentOtService.listOperationsAfterVersion(60L, 1L, -1L, null))
                .thenThrow(new IllegalArgumentException("afterVersion is required and must be >= 0"));

        ApiResponse<List<DocumentOperation>> response = documentController.getOperationsAfterVersion(60L, -1L, null);

        assertEquals(400, response.getCode());
    }

    @Test
    void getOperationsAfterVersionShouldReturn400ForInvalidLimit() {
        AuthContext.setUserId(1L);
        when(documentOtService.listOperationsAfterVersion(60L, 1L, 0L, 2000))
                .thenThrow(new IllegalArgumentException("limit must be between 1 and 1000"));

        ApiResponse<List<DocumentOperation>> response = documentController.getOperationsAfterVersion(60L, 0L, 2000);

        assertEquals(400, response.getCode());
    }

    @Test
    void getOperationsAfterVersionShouldReturn403WhenNoReadPermission() {
        AuthContext.setUserId(2L);
        when(documentOtService.listOperationsAfterVersion(61L, 2L, 3L, null))
                .thenThrow(new IllegalArgumentException("No permission to read this document"));

        ApiResponse<List<DocumentOperation>> response = documentController.getOperationsAfterVersion(61L, 3L, null);

        assertEquals(403, response.getCode());
    }

    @Test
    void getOperationsAfterVersionShouldReturnOperationList() {
        AuthContext.setUserId(3L);

        DocumentOperation op = new DocumentOperation();
        op.setDocumentId(62L);
        op.setServerVersion(4L);
        op.setClientOpId("op-1");

        when(documentOtService.listOperationsAfterVersion(62L, 3L, 1L, 200))
                .thenReturn(Collections.singletonList(op));

        ApiResponse<List<DocumentOperation>> response = documentController.getOperationsAfterVersion(62L, 1L, 200);

        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals(Long.valueOf(4L), response.getData().get(0).getServerVersion());
    }

    @Test
    void getLatestStateShouldReturnDocumentForReadableUser() {
        AuthContext.setUserId(1L);
        Document doc = buildDoc(15L, 1L);
        when(documentService.getById(15L)).thenReturn(doc);
        when(documentService.canRead(1L, doc)).thenReturn(true);

        ApiResponse<Document> response = documentController.getLatestState(15L);

        assertEquals(200, response.getCode());
        assertEquals(Long.valueOf(15L), response.getData().getId());
    }

    @Test
    void getLatestStateShouldReturn404WhenDocumentNotFound() {
        AuthContext.setUserId(1L);
        when(documentService.getById(16L)).thenReturn(null);

        ApiResponse<Document> response = documentController.getLatestState(16L);

        assertEquals(404, response.getCode());
    }

    @Test
    void getLatestSnapshotShouldReturnSnapshotWhenReadable() {
        AuthContext.setUserId(1L);
        Document doc = buildDoc(70L, 1L);
        DocumentSnapshot snapshot = new DocumentSnapshot();
        snapshot.setDocumentId(70L);
        snapshot.setSnapshotVersion(200L);
        snapshot.setContent("Hello");

        when(documentService.getById(70L)).thenReturn(doc);
        when(documentService.canRead(1L, doc)).thenReturn(true);
        when(documentSnapshotService.getLatestSnapshotBeforeVersion(70L, 200L)).thenReturn(snapshot);

        ApiResponse<DocumentSnapshot> response = documentController.getLatestSnapshot(70L, 200L);

        assertEquals(200, response.getCode());
        assertEquals(Long.valueOf(200L), response.getData().getSnapshotVersion());
    }

    @Test
    void reconstructContentShouldReturnContentWhenReadable() {
        AuthContext.setUserId(1L);
        Document doc = buildDoc(71L, 1L);

        when(documentService.getById(71L)).thenReturn(doc);
        when(documentService.canRead(1L, doc)).thenReturn(true);
        when(documentSnapshotService.reconstructContent(71L, 205L)).thenReturn("ABC");

        ApiResponse<String> response = documentController.reconstructContent(71L, 205L);

        assertEquals(200, response.getCode());
        assertEquals("ABC", response.getData());
    }

    private Document buildDoc(Long id, Long ownerId) {
        Document document = new Document();
        document.setId(id);
        document.setOwnerUserId(ownerId);
        return document;
    }
}
