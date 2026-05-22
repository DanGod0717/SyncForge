package com.example.syncforge.document.service;

import com.example.syncforge.document.entity.DocumentOperation;
import com.example.syncforge.realtime.ot.OtEditMessage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TextOperationTransformer {
    // 操作转换
    //将一个基于旧版本（baseVersion）生成的操作 incoming，与自该版本
    // 之后已经应用到文档上的所有历史操作 appliedAfterBase 进行 转换，生成一个可以在当前最新文档上执行的新操作。
    //incoming：新到达的操作（例如用户 B 的插入或删除），它是基于某个旧版本（比如版本 5）生成的
    //在 incoming 所基于的旧版本之后，已经实际应用到文档上的历史操作列表（按时间顺序排列）。
    public OtEditMessage transform(OtEditMessage incoming, List<DocumentOperation> appliedAfterBase) {
        // 复制新到达的操作避免直接修改原对象（因为原对象可能还需要保留用于其他用途，比如重传或日志）。
        OtEditMessage transformed = copy(incoming);
        //
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
    //它用一个历史操作 history 去调整当前操作 incoming 的位置/长度，使 incoming 能够在对 history 施加后的文档状态上正确执行。
    private void transformAgainstOne(OtEditMessage incoming, DocumentOperation history) {
        //如果 history 为空，或者缺少必要字段（position 或 opType），则直接返回，不做任何转换。
        if (history == null || history.getPosition() == null || history.getOpType() == null) {
            return;
        }
        //    如果 incoming 是插入操作 → 调用 transformInsert(incoming, history)
        //
        //    否则（即删除操作） → 调用 transformDelete(incoming, history)
        if ("insert".equals(incoming.getOpType())) {
            transformInsert(incoming, history);
            return;
        }
        transformDelete(incoming, history);
    }

    private void transformInsert(OtEditMessage incoming, DocumentOperation history) {
        // 当前要插入的位置
        int position = incoming.getPosition();
        // 前一个操作的位置
        int historyPosition = history.getPosition();
        if ("insert".equals(history.getOpType())) {
            // 如果 history 是插入操作，并且它的位置在当前插入位置之前，那么当前插入位置需要向后移动，移动的距离等于 history 插入文本的长度。
            int historyLen = safeLength(history.getContent());
            if (historyPosition <= position) {
                incoming.setPosition(position + historyLen);
            }
            return;
        }
        // 如果是删除操作，计算删除长度
        int historyDeleteLength = safeInt(history.getDeleteLength());
        if (historyDeleteLength <= 0) {
            return;
        }
        if (historyPosition < position) {
            // 删除的位置在我插入位置之前，则需要移动道歉position
            // 删除产犊和 当前操作位置与删除的操作位置差值比较，如果删除长度大于这个差值，说明我的插入位置被删除了，那么就把我的插入位置移动到删除位置
            int moved = Math.min(historyDeleteLength, position - historyPosition);
            incoming.setPosition(position - moved);
        }
    }
    //    复制当前操作。
    //
    //    按顺序用每个历史操作调用单步转换。
    //
    //    单步转换根据当前操作类型（插入/删除）和历史操作类型，调整位置、长度等参数。
    //
    //    返回已调整的操作，该操作可直接应用到最新文档上，保证与“先应用所有历史操作，再应用原始操作”的结果一致。
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
                // 最复杂 重叠删除趋于
                int overlapStart = Math.max(start, historyPosition);
                int overlapEnd = Math.min(end, historyEnd);
                // 覆盖的start和end
                // 覆盖的长度
                int overlapLength = Math.max(0, overlapEnd - overlapStart);
                // start 左偏移量
                // 如果start在historyPosition 右边，则start-historyPosition 为正，然后比较历史删除长度，如果历史删除长度大于start-historyPosition
                // 说明start只需要左移
                // historyDeleteLength 删除长度大于 start-historyPosition ，实际只需要start 左移start-historyPosition 不能
                // 删除长度小于 start-historyPosition，start 左移 historyDeleteLength 就可以了
                int leftShift = Math.min(Math.max(start - historyPosition, 0), historyDeleteLength);
                start -= leftShift;
                end -= overlapLength + leftShift;
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

