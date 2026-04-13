package com.example.syncforge.document.service;

import com.example.syncforge.document.entity.Document;
import com.example.syncforge.document.entity.DocumentPermission;
import com.example.syncforge.document.mapper.DocumentMapper;
import com.example.syncforge.document.mapper.DocumentPermissionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
//告诉 JUnit 启用 Mockito 扩展，允许使用 @Mock、@InjectMocks 等注解。
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {
    //创建 DocumentMapper 和 DocumentPermissionMapper 的模拟对象，不会真正操作数据库。
    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private DocumentPermissionMapper documentPermissionMapper;
    //创建 DocumentService 的实例，并将上面两个模拟对象自动注入到该实例中（通过构造器或属性注入）。
    @InjectMocks
    private DocumentService documentService;
    //DocumentService 的逻辑，而它的依赖行为完全由测试控制

    // 模拟插入成功
    @Test
    void createShouldSetDefaultsAndOwnerFields() {
        Document input = new Document();
        input.setTitle("doc-a");
        input.setContent("hello");
        // 模拟插入行为 无论插入怎么样的都返回1
        when(documentMapper.insert(any(Document.class))).thenReturn(1);
        // 传入 document input 和 拥有者id 100
        int affected = documentService.create(input, 100L);
        // 断言
        assertEquals(1, affected);
        // 插入成功后返回的input version 还有isIsDeleted
        assertEquals(0L, input.getVersion());
        assertEquals(Integer.valueOf(0), input.getIsDeleted());
        // 验证插入后返回的拥有者
        assertEquals(Long.valueOf(100L), input.getOwnerUserId());
        assertEquals(Long.valueOf(100L), input.getLastEditUserId());
    }
    // Owner 是否可以阅读
    @Test
    void canReadShouldReturnTrueForOwner() {
        Document doc = new Document();
        doc.setId(1L);
        doc.setOwnerUserId(10L);

        assertTrue(documentService.canRead(10L, doc));
    }
    // 阅读者可以阅读
    @Test
    void canReadShouldReturnTrueForViewerPermission() {
        Document doc = new Document();
        doc.setId(2L);
        doc.setOwnerUserId(10L);

        when(documentPermissionMapper.findPermissionLevelByDocumentIdAndUserId(2L, 11L)).thenReturn("viewer");

        assertTrue(documentService.canRead(11L, doc));
    }

    @Test
    void canEditShouldReturnFalseForViewerPermission() {
        Document doc = new Document();
        doc.setId(3L);
        doc.setOwnerUserId(10L);

        when(documentPermissionMapper.findPermissionLevelByDocumentIdAndUserId(3L, 12L)).thenReturn("viewer");

        assertFalse(documentService.canEdit(12L, doc));
    }
// 更新后 版本冲突应该不可以阅读
    @Test
    void updateContentShouldReturnFalseWhenVersionConflict() {
        // 配置了行为 调用这个会返回 mapper 会返回0 然后根据服务 最终更新失败 因为版本号是0
        when(documentMapper.updateContentByIdAndVersion(9L, "new", 1L, 77L)).thenReturn(0);

        boolean updated = documentService.updateContent(9L, "new", 1L, 77L);

        assertFalse(updated);
    }

    @Test
    void grantPermissionShouldUsePermissionLevelAndGrantedBy() {
        when(documentPermissionMapper.upsertPermission(any(DocumentPermission.class))).thenReturn(1);

        boolean granted = documentService.grantPermission(5L, 8L, "editor", 1L);

        assertTrue(granted);

        ArgumentCaptor<DocumentPermission> captor = ArgumentCaptor.forClass(DocumentPermission.class);
        verify(documentPermissionMapper).upsertPermission(captor.capture());
        DocumentPermission permission = captor.getValue();

        assertEquals(Long.valueOf(5L), permission.getDocumentId());
        assertEquals(Long.valueOf(8L), permission.getUserId());
        assertEquals("editor", permission.getPermissionLevel());
        assertEquals(Long.valueOf(1L), permission.getGrantedBy());
    }

    @Test
    void revokePermissionShouldReturnFalseWhenNothingChanged() {
        when(documentPermissionMapper.softDeletePermission(eq(5L), eq(8L))).thenReturn(0);

        boolean revoked = documentService.revokePermission(5L, 8L);

        assertFalse(revoked);
    }

    @Test
    void listMineShouldUseDefaultPagingWhenParamsAreNull() {
        when(documentMapper.findMineByUserId(10L, 0, 20)).thenReturn(Collections.emptyList());

        documentService.listMine(10L, null, null);

        verify(documentMapper).findMineByUserId(10L, 0, 20);
    }

    @Test
    void listSharedWithMeShouldCapPageSizeToMax() {
        when(documentMapper.findSharedWithMeByUserId(11L, 0, 100)).thenReturn(Collections.emptyList());

        documentService.listSharedWithMe(11L, 1, 999);

        verify(documentMapper).findSharedWithMeByUserId(11L, 0, 100);
    }

    @Test
    void listMineShouldComputeOffsetFromPageAndSize() {
        when(documentMapper.findMineByUserId(12L, 20, 10)).thenReturn(Collections.emptyList());

        documentService.listMine(12L, 3, 10);

        verify(documentMapper).findMineByUserId(12L, 20, 10);
    }
}
