package com.example.syncforge.document.service;

import com.example.syncforge.document.entity.DocumentOperation;
import com.example.syncforge.realtime.ot.OtEditMessage;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextOperationTransformerTest {

    private final TextOperationTransformer transformer = new TextOperationTransformer();

    @Test
    void transformShouldShiftInsertRightWhenTwoInsertsAtSamePosition() {
        OtEditMessage incoming = op("op-1", 0L, "insert", 2, "B", null);
        DocumentOperation historyInsert = history("insert", 2, "AA", null);

        OtEditMessage transformed = transformer.transform(incoming, Collections.singletonList(historyInsert));

        assertEquals(4, transformed.getPosition());
        assertEquals("B", transformed.getContent());
    }

    @Test
    void transformShouldMoveInsertLeftWhenHistoryDeleteBeforePosition() {
        OtEditMessage incoming = op("op-2", 0L, "insert", 5, "X", null);
        DocumentOperation historyDelete = history("delete", 2, null, 2);

        OtEditMessage transformed = transformer.transform(incoming, Collections.singletonList(historyDelete));

        assertEquals(3, transformed.getPosition());
    }

    @Test
    void transformShouldHandleOverlappedDeleteRanges() {
        OtEditMessage incoming = op("op-3", 0L, "delete", 4, null, 4);
        DocumentOperation historyDelete = history("delete", 2, null, 4);

        OtEditMessage transformed = transformer.transform(incoming, Collections.singletonList(historyDelete));

        assertEquals(2, transformed.getPosition());
        assertEquals(2, transformed.getDeleteLength());
    }

    @Test
    void transformShouldNotDoubleDeleteWhenRangesPartiallyOverlap() {
        OtEditMessage incoming = op("op-6", 0L, "delete", 2, null, 1);
        DocumentOperation historyDelete = history("delete", 1, null, 2);

        OtEditMessage transformed = transformer.transform(incoming, Collections.singletonList(historyDelete));

        assertEquals(1, transformed.getPosition());
        assertEquals(0, transformed.getDeleteLength());

        String afterHistory = transformer.apply("ABCDEFG", op("h", 0L, "delete", 1, null, 2));
        String afterBoth = transformer.apply(afterHistory, transformed);
        assertEquals("ADEFG", afterBoth);
    }

    @Test
    void applyShouldInsertContentAtExpectedPosition() {
        OtEditMessage insert = op("op-4", 0L, "insert", 1, "XYZ", null);

        String next = transformer.apply("abc", insert);

        assertEquals("aXYZbc", next);
    }

    @Test
    void applyShouldDeleteRequestedRange() {
        OtEditMessage delete = op("op-5", 0L, "delete", 1, null, 3);

        String next = transformer.apply("abcdef", delete);

        assertEquals("aef", next);
    }

    private OtEditMessage op(String clientOpId,
                             Long baseVersion,
                             String opType,
                             Integer position,
                             String content,
                             Integer deleteLength) {
        OtEditMessage msg = new OtEditMessage();
        msg.setClientOpId(clientOpId);
        msg.setBaseVersion(baseVersion);
        msg.setOpType(opType);
        msg.setPosition(position);
        msg.setContent(content);
        msg.setDeleteLength(deleteLength);
        return msg;
    }

    private DocumentOperation history(String opType, Integer position, String content, Integer deleteLength) {
        DocumentOperation operation = new DocumentOperation();
        operation.setOpType(opType);
        operation.setPosition(position);
        operation.setContent(content);
        operation.setDeleteLength(deleteLength);
        return operation;
    }
}

