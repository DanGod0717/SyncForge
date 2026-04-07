package com.example.syncforge.document.mapper;

import com.example.syncforge.document.entity.Document;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DocumentMapper {
    Document findById(@Param("id") Long id);
    // 插入文档
    int insert(Document document);
    // 更新内容
    int updateContentByIdAndVersion(@Param("id") Long id,
                                    @Param("content") String content,
                                    @Param("version") Long version);

    int softDeleteById(@Param("id") Long id);
}
