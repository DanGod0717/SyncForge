package com.example.syncforge.document.service;

import com.example.syncforge.document.entity.DocumentOperation;
import com.example.syncforge.document.entity.DocumentSnapshot;
import com.example.syncforge.document.mapper.DocumentOperationMapper;
import com.example.syncforge.document.mapper.DocumentSnapshotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentSnapshotServiceTest {

    @Mock
    private DocumentSnapshotMapper documentSnapshotMapper;

    @Mock
    private DocumentOperationMapper documentOperationMapper;

    private DocumentSnapshotService documentSnapshotService;

    @BeforeEach
    void setUp() {
        documentSnapshotService = new DocumentSnapshotService(
                documentSnapshotMapper,
                documentOperationMapper,
                new TextOperationTransformer()
        );
    }

    @Test
    void maybeCreateSnapshotShouldInsertWhenVersionHitsInterval() {
        when(documentSnapshotMapper.findByDocumentIdAndSnapshotVersion(1L, 200L)).thenReturn(null);

        documentSnapshotService.maybeCreateSnapshot(1L, 200L, "Title", "Hello", 9L);

        ArgumentCaptor<DocumentSnapshot> captor = ArgumentCaptor.forClass(DocumentSnapshot.class);
        verify(documentSnapshotMapper).insert(captor.capture());
        DocumentSnapshot snapshot = captor.getValue();
        assertEquals(Long.valueOf(1L), snapshot.getDocumentId());
        assertEquals(Long.valueOf(200L), snapshot.getSnapshotVersion());
        assertEquals("Title", snapshot.getTitle());
        assertEquals("Hello", snapshot.getContent());
        assertNotNull(snapshot.getContentHash());
        assertEquals(Long.valueOf(9L), snapshot.getCreatedBy());
    }

    @Test
    void maybeCreateSnapshotShouldSkipWhenVersionNotHitInterval() {
        documentSnapshotService.maybeCreateSnapshot(1L, 199L, "Title", "Hello", 9L);

        verify(documentSnapshotMapper, never()).findByDocumentIdAndSnapshotVersion(any(Long.class), any(Long.class));
        verify(documentSnapshotMapper, never()).insert(any(DocumentSnapshot.class));
    }

    @Test
    void reconstructContentShouldReplaySnapshotAndOperations() {
        DocumentSnapshot snapshot = new DocumentSnapshot();
        snapshot.setDocumentId(1L);
        snapshot.setSnapshotVersion(200L);
        snapshot.setContent("AB");

        DocumentOperation insertC = new DocumentOperation();
        insertC.setClientOpId("op-1");
        insertC.setBaseVersion(200L);
        insertC.setOpType("insert");
        insertC.setPosition(2);
        insertC.setContent("C");

        DocumentOperation deleteB = new DocumentOperation();
        deleteB.setClientOpId("op-2");
        deleteB.setBaseVersion(201L);
        deleteB.setOpType("delete");
        deleteB.setPosition(1);
        deleteB.setDeleteLength(1);

        when(documentSnapshotMapper.findLatestBeforeVersion(1L, 202L)).thenReturn(snapshot);
        when(documentOperationMapper.findAfterVersion(1L, 200L)).thenReturn(Arrays.asList(insertC, deleteB));

        String content = documentSnapshotService.reconstructContent(1L, 202L);

        assertEquals("AC", content);
    }

    @Test
    void reconstructContentShouldReturnEmptyWhenNoSnapshotAndNoOps() {
        when(documentSnapshotMapper.findLatestBeforeVersion(1L, 10L)).thenReturn(null);
        when(documentOperationMapper.findAfterVersion(1L, 0L)).thenReturn(Collections.emptyList());

        String content = documentSnapshotService.reconstructContent(1L, 10L);

        assertEquals("", content);
    }
}

