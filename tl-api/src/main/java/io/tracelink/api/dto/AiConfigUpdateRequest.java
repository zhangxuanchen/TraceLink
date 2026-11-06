package io.tracelink.api.dto;

/** 模型配置保存：密钥留空表示沿用当前值，只改接口地址或模型 ID 时不用重新粘贴 */
public record AiConfigUpdateRequest(String baseUrl, String chatModel, String apiKey) {
}
