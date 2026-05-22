package com.example.syncforge.document.service;

import com.example.syncforge.document.entity.Document;
import com.example.syncforge.document.entity.DocumentOperation;
import com.example.syncforge.document.mapper.DocumentMapper;
import com.example.syncforge.document.mapper.DocumentOperationMapper;
import com.example.syncforge.realtime.ot.OtApplyResult;
import com.example.syncforge.realtime.ot.OtEditMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentOtServiceTest {

    @Mock
    private DocumentService documentService;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private DocumentOperationMapper documentOperationMapper;

    @Mock
    private DocumentSnapshotService documentSnapshotService;

    private DocumentOtService documentOtService;

    @BeforeEach
    void setUp() {
        documentOtService = new DocumentOtService(
                documentService,
                documentMapper,
                documentOperationMapper,
                documentSnapshotService,
                new TextOperationTransformer()
        );
    }

    @Test
    void applyClientOperationShouldTransformWhenBaseVersionIsOld() {
        Document document = doc(9L, 1L, 3L, "abcde");
        OtEditMessage message = insertOp("op-old-base", 1L, 2, "X");

        DocumentOperation historyInsert = new DocumentOperation();
        historyInsert.setOpType("insert");
        historyInsert.setPosition(1);
        historyInsert.setContent("YY");

        when(documentMapper.findByIdForUpdate(9L)).thenReturn(document);
        when(documentService.canEdit(1L, document)).thenReturn(true);
        when(documentOperationMapper.findAfterVersion(9L, 1L)).thenReturn(Collections.singletonList(historyInsert));
        when(documentMapper.updateContentByIdAndVersion(eq(9L), eq("abcdXe"), eq(3L), eq(1L))).thenReturn(1);
        when(documentOperationMapper.insert(any(DocumentOperation.class))).thenReturn(1);

        OtApplyResult result = documentOtService.applyClientOperation(9L, 1L, message);

        assertNotNull(result);
        assertEquals(Long.valueOf(4L), result.getAck().getServerVersion());
        assertEquals(Long.valueOf(4L), result.getEvent().getServerVersion());

        ArgumentCaptor<DocumentOperation> captor = ArgumentCaptor.forClass(DocumentOperation.class);
        verify(documentOperationMapper).insert(captor.capture());
        DocumentOperation saved = captor.getValue();
        assertEquals(Integer.valueOf(4), saved.getPosition());
        assertEquals("insert", saved.getOpType());
        assertEquals("X", saved.getContent());
    }

    @Test
    void applyClientOperationShouldThrowWhenPositionOutOfRange() {
        Document document = doc(10L, 2L, 0L, "abc");
        OtEditMessage message = insertOp("op-range", 0L, 5, "x");

        when(documentMapper.findByIdForUpdate(10L)).thenReturn(document);
        when(documentService.canEdit(2L, document)).thenReturn(true);
        when(documentOperationMapper.findAfterVersion(10L, 0L)).thenReturn(Collections.emptyList());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> documentOtService.applyClientOperation(10L, 2L, message));

        assertEquals("position out of range", ex.getMessage());
    }

    @Test
    void applyClientOperationShouldThrowWhenBaseVersionAheadOfServerVersion() {
        Document document = doc(11L, 2L, 2L, "abc");
        OtEditMessage message = insertOp("op-ahead", 3L, 1, "x");

        when(documentMapper.findByIdForUpdate(11L)).thenReturn(document);
        when(documentService.canEdit(2L, document)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> documentOtService.applyClientOperation(11L, 2L, message));

        assertEquals("baseVersion is ahead of server version", ex.getMessage());
    }

    @Test
    void applyClientOperationShouldThrowWhenClientOpIdDuplicated() {
        Document document = doc(12L, 2L, 5L, "abc");
        OtEditMessage message = insertOp("dup-op", 5L, 3, "x");

        when(documentMapper.findByIdForUpdate(12L)).thenReturn(document);
        when(documentService.canEdit(2L, document)).thenReturn(true);
        when(documentOperationMapper.findAfterVersion(12L, 5L)).thenReturn(Collections.emptyList());
        when(documentMapper.updateContentByIdAndVersion(eq(12L), eq("abcx"), eq(5L), eq(2L))).thenReturn(1);
        when(documentOperationMapper.insert(any(DocumentOperation.class))).thenThrow(new DuplicateKeyException("dup"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> documentOtService.applyClientOperation(12L, 2L, message));

        assertEquals("Duplicate clientOpId for this document and user", ex.getMessage());
    }

    @Test
    void applyClientOperationShouldReturnSameAckWhenClientOpIdDuplicated() {
        Document document = doc(12L, 2L, 5L, "abc");
        OtEditMessage message = insertOp("dup-op", 5L, 3, "x");
        DocumentOperation duplicated = new DocumentOperation();
        duplicated.setDocumentId(12L);
        duplicated.setAuthorUserId(2L);
        duplicated.setClientOpId("dup-op");
        duplicated.setServerVersion(6L);

        when(documentOperationMapper.findByClientOp(12L, 2L, "dup-op")).thenReturn(null, duplicated);
        when(documentMapper.findByIdForUpdate(12L)).thenReturn(document);
        when(documentService.canEdit(2L, document)).thenReturn(true);
        when(documentOperationMapper.findAfterVersion(12L, 5L)).thenReturn(Collections.emptyList());
        when(documentMapper.updateContentByIdAndVersion(eq(12L), eq("abcx"), eq(5L), eq(2L))).thenReturn(1);
        when(documentOperationMapper.insert(any(DocumentOperation.class))).thenThrow(new DuplicateKeyException("dup"));

        OtApplyResult result = documentOtService.applyClientOperation(12L, 2L, message);

        assertEquals(Long.valueOf(6L), result.getAck().getServerVersion());
        assertEquals("dup-op", result.getAck().getClientOpId());
        assertNull(result.getEvent());
    }

    @Test
    void applyClientOperationShouldShortCircuitWhenClientOpAlreadyExists() {
        OtEditMessage message = insertOp("replay-op", 1L, 0, "x");
        DocumentOperation existing = new DocumentOperation();
        existing.setServerVersion(9L);

        when(documentOperationMapper.findByClientOp(44L, 7L, "replay-op")).thenReturn(existing);

        OtApplyResult result = documentOtService.applyClientOperation(44L, 7L, message);

        assertEquals(Long.valueOf(9L), result.getAck().getServerVersion());
        assertNull(result.getEvent());
        verify(documentService, never()).getById(any(Long.class));
        verify(documentMapper, never()).updateContentByIdAndVersion(any(Long.class), any(String.class), any(Long.class), any(Long.class));
        verify(documentOperationMapper, never()).insert(any(DocumentOperation.class));
    }

    @Test
    void applyClientOperationShouldThrowWhenConcurrentUpdateDetected() {
        Document firstRead = doc(30L, 3L, 5L, "abc");
        OtEditMessage message = insertOp("op-retry", 5L, 3, "x");

        when(documentOperationMapper.findByClientOp(30L, 3L, "op-retry")).thenReturn(null);
        when(documentMapper.findByIdForUpdate(30L)).thenReturn(firstRead);
        when(documentService.canEdit(3L, firstRead)).thenReturn(true);
        when(documentOperationMapper.findAfterVersion(30L, 5L)).thenReturn(Collections.emptyList());
        when(documentMapper.updateContentByIdAndVersion(30L, "abcx", 5L, 3L)).thenReturn(0);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> documentOtService.applyClientOperation(30L, 3L, message));

        assertEquals("Concurrent update detected", ex.getMessage());
    }

    @RepeatedTest(5)
    void applyClientOperationShouldKeepMonotonicServerVersionForSameInput() {
        Document beforeFirst = doc(20L, 9L, 5L, "abc");
        Document beforeSecond = doc(20L, 9L, 6L, "abcx");

        when(documentMapper.findByIdForUpdate(20L)).thenReturn(beforeFirst, beforeSecond);
        when(documentService.canEdit(eq(9L), any(Document.class))).thenReturn(true);
        when(documentOperationMapper.findAfterVersion(eq(20L), any(Long.class))).thenReturn(Collections.emptyList());
        when(documentMapper.updateContentByIdAndVersion(any(Long.class), any(String.class), any(Long.class), any(Long.class)))
                .thenReturn(1);
        when(documentOperationMapper.insert(any(DocumentOperation.class))).thenReturn(1);

        OtApplyResult first = documentOtService.applyClientOperation(20L, 9L, insertOp("op-1", 5L, 3, "x"));
        OtApplyResult second = documentOtService.applyClientOperation(20L, 9L, insertOp("op-2", 6L, 4, "y"));

        assertEquals(Long.valueOf(6L), first.getAck().getServerVersion());
        assertEquals(Long.valueOf(7L), second.getAck().getServerVersion());
    }

    @Test
    void applyClientOperationShouldCreateSnapshotWhenVersionHitsInterval() {
        Document document = doc(31L, 8L, 199L, "abc");
        OtEditMessage message = insertOp("op-snapshot", 199L, 3, "x");

        when(documentMapper.findByIdForUpdate(31L)).thenReturn(document);
        when(documentService.canEdit(8L, document)).thenReturn(true);
        when(documentOperationMapper.findAfterVersion(31L, 199L)).thenReturn(Collections.emptyList());
        when(documentMapper.updateContentByIdAndVersion(eq(31L), eq("abcx"), eq(199L), eq(8L))).thenReturn(1);
        when(documentOperationMapper.insert(any(DocumentOperation.class))).thenReturn(1);

        documentOtService.applyClientOperation(31L, 8L, message);

        verify(documentSnapshotService).maybeCreateSnapshot(31L, 200L, document.getTitle(), "abcx", 8L);
    }

    @Test
    void listOperationsAfterVersionShouldThrowWhenAfterVersionInvalid() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> documentOtService.listOperationsAfterVersion(1L, 1L, -1L, null));

        assertEquals("afterVersion is required and must be >= 0", ex.getMessage());
    }

    @Test
    void listOperationsAfterVersionShouldThrowWhenLimitInvalid() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> documentOtService.listOperationsAfterVersion(1L, 1L, 0L, 0));

        assertEquals("limit must be between 1 and 1000", ex.getMessage());
    }

    @Test
    void listOperationsAfterVersionShouldThrowWhenDocumentNotFound() {
        when(documentService.getById(99L)).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> documentOtService.listOperationsAfterVersion(99L, 1L, 0L, 200));

        assertEquals("Document not found", ex.getMessage());
    }

    @Test
    void listOperationsAfterVersionShouldThrowWhenNoReadPermission() {
        Document document = doc(88L, 2L, 1L, "abc");
        when(documentService.getById(88L)).thenReturn(document);
        when(documentService.canRead(3L, document)).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> documentOtService.listOperationsAfterVersion(88L, 3L, 0L, 200));

        assertEquals("No permission to read this document", ex.getMessage());
    }

    @Test
    void listOperationsAfterVersionShouldReturnOperationList() {
        Document document = doc(77L, 2L, 6L, "abc");
        DocumentOperation op1 = new DocumentOperation();
        op1.setServerVersion(5L);
        DocumentOperation op2 = new DocumentOperation();
        op2.setServerVersion(6L);

        when(documentService.getById(77L)).thenReturn(document);
        when(documentService.canRead(2L, document)).thenReturn(true);
        when(documentOperationMapper.findAfterVersionWithLimit(77L, 4L, 200)).thenReturn(Arrays.asList(op1, op2));

        assertEquals(2, documentOtService.listOperationsAfterVersion(77L, 2L, 4L, 200).size());
    }

    @Test
    void listOperationsAfterVersionShouldUseDefaultLimitWhenLimitMissing() {
        Document document = doc(66L, 2L, 6L, "abc");
        when(documentService.getById(66L)).thenReturn(document);
        when(documentService.canRead(2L, document)).thenReturn(true);
        when(documentOperationMapper.findAfterVersionWithLimit(66L, 4L, 200)).thenReturn(Collections.emptyList());

        documentOtService.listOperationsAfterVersion(66L, 2L, 4L, null);

        verify(documentOperationMapper).findAfterVersionWithLimit(66L, 4L, 200);
    }

    private Document doc(Long id, Long ownerUserId, Long version, String content) {
        Document document = new Document();
        document.setId(id);
        document.setOwnerUserId(ownerUserId);
        document.setVersion(version);
        document.setContent(content);
        return document;
    }

    private OtEditMessage insertOp(String clientOpId, Long baseVersion, Integer position, String content) {
        OtEditMessage message = new OtEditMessage();
        message.setClientOpId(clientOpId);
        message.setBaseVersion(baseVersion);
        message.setOpType("insert");
        message.setPosition(position);
        message.setContent(content);
        return message;
    }
}
