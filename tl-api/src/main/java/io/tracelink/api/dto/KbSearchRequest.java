package io.tracelink.api.dto;

/** 知识检索入参，字段可缺省，范围由登录账号的数据范围决定 */
public record KbSearchRequest(String question, String domain, Long deptId, Integer topK) {
}
