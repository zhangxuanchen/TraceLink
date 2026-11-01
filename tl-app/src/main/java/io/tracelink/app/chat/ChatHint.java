package io.tracelink.app.chat;

/** 建单入口提示：会话不产出草稿，只给链接与说明（Q12） */
public record ChatHint(String text, String url, String action) {
}
