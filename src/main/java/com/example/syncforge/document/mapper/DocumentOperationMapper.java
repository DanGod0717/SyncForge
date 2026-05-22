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

    List<DocumentOperation> findAfterVersionWithLimit(@Param("documentId") Long documentId,
                                                      @Param("version") Long version,
                                                      @Param("limit") Integer limit);
    List<DocumentOperation> findBetweenVersions(@Param("documentId") Long documentId,
                                                @Param("fromVersion") Long fromVersion,
                                                @Param("toVersion") Long toVersion);
    // 按照 文档+用户+客户端操作ID 检查是否已经处理过这个操作
    // 同一 clientOpId 重发时，服务端识别为“已处理”，不做第二次写入，直接返回之前的结果，保证幂等性
    DocumentOperation findByClientOp(@Param("documentId") Long documentId,
                                     @Param("authorUserId") Long authorUserId,
                                     @Param("clientOpId") String clientOpId);
}
