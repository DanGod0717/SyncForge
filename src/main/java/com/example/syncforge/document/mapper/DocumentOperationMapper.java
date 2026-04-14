package com.example.syncforge.document.mapper;

import com.example.syncforge.document.entity.DocumentOperation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocumentOperationMapper {
    int insert(DocumentOperation operation);

    List<DocumentOperation> findAfterVersion(@Param("documentId") Long documentId,
                                             @Param("version") Long version);
}

