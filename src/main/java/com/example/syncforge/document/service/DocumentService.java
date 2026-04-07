package com.example.syncforge.document.service;

import com.example.syncforge.document.entity.Document;
import com.example.syncforge.document.mapper.DocumentMapper;
import org.springframework.stereotype.Service;

@Service
public class DocumentService {

    private final DocumentMapper documentMapper;

    public DocumentService(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    public Document getById(Long id) {
        return documentMapper.findById(id);
    }
    //创建文档
    public int create(Document document) {
        if (document.getVersion() == null) {
            document.setVersion(0L);
        }
        // 逻辑删除 0 未删除 1 已经删除
        if (document.getIsDeleted() == null) {
            document.setIsDeleted(0);
        }
        return documentMapper.insert(document);
    }
    //当前version == 你传入的version
    public boolean updateContent(Long id, String content, Long version) {
        return documentMapper.updateContentByIdAndVersion(id, content, version) > 0;
    }

    public boolean softDelete(Long id) {
        return documentMapper.softDeleteById(id) > 0;
    }
}