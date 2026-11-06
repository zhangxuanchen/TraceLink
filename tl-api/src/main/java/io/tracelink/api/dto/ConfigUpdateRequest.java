package io.tracelink.api.dto;

/** 参数修改：只改值，键与类型由库里的定义决定 */
public record ConfigUpdateRequest(String value) {
}
