package io.tracelink.app.chat;

import java.util.List;
import java.util.Map;

/** 工具执行结果：facts 供规则回答与模型提示词使用，event 是发回页面的精简版 */
public record ChatToolResult(
        String name,
        String label,
        Map<String, Object> args,
        int resultCount,
        long elapsedMs,
        List<String> facts) {

    public ChatToolEvent toEvent() {
        return new ChatToolEvent(name, label, args, resultCount, elapsedMs);
    }
}
