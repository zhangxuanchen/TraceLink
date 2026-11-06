package io.tracelink.domain.ai;

/** 运行期模型配置：页面里保存的值优先，没保存过的项回落到环境变量 */
public record AiSettings(String baseUrl, String apiKey, String chatModel) {

    public String normalizedBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://ark.cn-beijing.volces.com/api/v3";
        }
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    /** 密钥与模型都填了才算可用，缺一项就按降级路径回答 */
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank() && chatModel != null && !chatModel.isBlank();
    }
}
