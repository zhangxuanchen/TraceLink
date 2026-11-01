package io.tracelink.app.chat;

import java.util.Map;

/** 工具调用事件：只给工具名、入参摘要、结果条数与耗时，不含明细正文 */
public record ChatToolEvent(
        String name,
        String label,
        Map<String, Object> args,
        int resultCount,
        long elapsedMs) {
}
