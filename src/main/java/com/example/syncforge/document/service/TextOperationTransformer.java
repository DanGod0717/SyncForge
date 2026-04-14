package com.example.syncforge.document.service;

import com.example.syncforge.document.entity.DocumentOperation;
import com.example.syncforge.realtime.ot.OtEditMessage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TextOperationTransformer {

    public OtEditMessage transform(OtEditMessage incoming, List<DocumentOperation> appliedAfterBase) {
        OtEditMessage transformed = copy(incoming);
        for (DocumentOperation history : appliedAfterBase) {
            transformAgainstOne(transformed, history);
        }
        return transformed;
    }

    public String apply(String currentContent, OtEditMessage operation) {
        String safeContent = currentContent == null ? "" : currentContent;
        int position = operation.getPosition();
        if ("insert".equals(operation.getOpType())) {
            String insertText = operation.getContent() == null ? "" : operation.getContent();
            return safeContent.substring(0, position) + insertText + safeContent.substring(position);
        }
        int deleteLength = operation.getDeleteLength();
        return safeContent.substring(0, position) + safeContent.substring(position + deleteLength);
    }

    private void transformAgainstOne(OtEditMessage incoming, DocumentOperation history) {
        if (history == null || history.getPosition() == null || history.getOpType() == null) {
            return;
        }
        if ("insert".equals(incoming.getOpType())) {
            transformInsert(incoming, history);
            return;
        }
        transformDelete(incoming, history);
    }

    private void transformInsert(OtEditMessage incoming, DocumentOperation history) {
        int position = incoming.getPosition();
        int historyPosition = history.getPosition();
        if ("insert".equals(history.getOpType())) {
            int historyLen = safeLength(history.getContent());
            if (historyPosition <= position) {
                incoming.setPosition(position + historyLen);
            }
            return;
        }
        int historyDeleteLength = safeInt(history.getDeleteLength());
        if (historyDeleteLength <= 0) {
            return;
        }
        if (historyPosition < position) {
            int moved = Math.min(historyDeleteLength, position - historyPosition);
            incoming.setPosition(position - moved);
        }
    }

    private void transformDelete(OtEditMessage incoming, DocumentOperation history) {
        int start = incoming.getPosition();
        int end = start + safeInt(incoming.getDeleteLength());
        int historyPosition = history.getPosition();

        if ("insert".equals(history.getOpType())) {
            int insertedLength = safeLength(history.getContent());
            if (insertedLength <= 0) {
                return;
            }
            if (historyPosition <= start) {
                start += insertedLength;
                end += insertedLength;
            } else if (historyPosition < end) {
                end += insertedLength;
            }
        } else {
            int historyDeleteLength = safeInt(history.getDeleteLength());
            if (historyDeleteLength <= 0) {
                return;
            }
            int historyEnd = historyPosition + historyDeleteLength;
            if (historyEnd <= start) {
                start -= historyDeleteLength;
                end -= historyDeleteLength;
            } else if (historyPosition >= end) {
                // no-op
            } else {
                int overlapStart = Math.max(start, historyPosition);
                int overlapEnd = Math.min(end, historyEnd);
                end -= Math.max(0, overlapEnd - overlapStart);
                if (historyPosition < start) {
                    start = historyPosition;
                }
            }
        }

        incoming.setPosition(start);
        incoming.setDeleteLength(Math.max(0, end - start));
    }

    private OtEditMessage copy(OtEditMessage source) {
        OtEditMessage target = new OtEditMessage();
        target.setClientOpId(source.getClientOpId());
        target.setBaseVersion(source.getBaseVersion());
        target.setOpType(source.getOpType());
        target.setPosition(source.getPosition());
        target.setContent(source.getContent());
        target.setDeleteLength(source.getDeleteLength());
        return target;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }
}

