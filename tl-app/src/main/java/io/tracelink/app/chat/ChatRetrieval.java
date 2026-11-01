package io.tracelink.app.chat;

import java.util.List;

/** 检索事件：切片 ID 与计划使用的工具名，页面先展示依据再等回答 */
public record ChatRetrieval(
        String retriever,
        boolean degraded,
        int total,
        List<Long> chunkIds,
        List<String> toolNames) {
}
