package io.tracelink.app.chat;

/** 结束事件：degraded 为真表示模型不可用或失败，本次回答由规则拼装 */
public record ChatDone(
        int answerChars,
        boolean degraded,
        String model,
        int latencyMs,
        String promptVer) {
}
