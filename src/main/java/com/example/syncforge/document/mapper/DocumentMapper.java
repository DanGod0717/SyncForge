package com.example.syncforge.document.mapper;

import com.example.syncforge.document.entity.Document;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocumentMapper {
    Document findById(@Param("id") Long id);
    Document findByIdForUpdate(@Param("id") Long id);
    // 插入文档
    int insert(Document document);
    // 更新内容
    int updateContentByIdAndVersion(@Param("id") Long id,
                                    @Param("content") String content,
                                    @Param("version") Long version,
                                    @Param("lastEditUserId") Long lastEditUserId);

    int softDeleteById(@Param("id") Long id,
                       @Param("operatorUserId") Long operatorUserId);

    List<Document> findMineByUserId(@Param("userId") Long userId,
                                    @Param("offset") int offset,
                                    @Param("size") int size);

    List<Document> findSharedWithMeByUserId(@Param("userId") Long userId,
                                            @Param("offset") int offset,
                                            @Param("size") int size);
}
