package io.tracelink.app.kb;

import io.tracelink.domain.kb.model.KbDomain;

/** 问答入参（KB-3），问题长度 3 至 500 字，topK 默认 6 */
public record KbAskCommand(String question, KbDomain domain, Long deptId, int topK) {
}
