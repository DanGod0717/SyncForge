package com.example.syncforge.document.mapper;

import com.example.syncforge.document.entity.DocumentSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocumentSnapshotMapper {

    int insert(DocumentSnapshot snapshot);

    DocumentSnapshot findByDocumentIdAndSnapshotVersion(@Param("documentId") Long documentId,
                                                        @Param("snapshotVersion") Long snapshotVersion);

    DocumentSnapshot findLatestBeforeVersion(@Param("documentId") Long documentId,
                                             @Param("snapshotVersion") Long snapshotVersion);

    List<DocumentSnapshot> findByDocumentId(@Param("documentId") Long documentId);
}

