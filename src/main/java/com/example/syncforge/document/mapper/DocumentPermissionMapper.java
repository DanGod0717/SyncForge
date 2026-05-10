package com.example.syncforge.document.mapper;

import com.example.syncforge.document.entity.DocumentPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DocumentPermissionMapper {
    String findPermissionLevelByDocumentIdAndUserId(@Param("documentId") Long documentId,
                                                    @Param("userId") Long userId);

    int upsertPermission(DocumentPermission permission);

    int softDeletePermission(@Param("documentId") Long documentId,
                             @Param("userId") Long userId);
}

