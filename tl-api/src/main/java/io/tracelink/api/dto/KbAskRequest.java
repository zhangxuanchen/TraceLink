package io.tracelink.api.dto;

/** 带引用问答入参（KB-3），问题长度 3 至 500 字 */
public record KbAskRequest(String question, String domain, Long deptId, Integer topK) {
}
