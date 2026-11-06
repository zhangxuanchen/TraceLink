package io.tracelink.ai.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.ai.AiSettings;
import io.tracelink.domain.ai.AiSettingsPort;
import io.tracelink.domain.ai.ChatPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Ark 对话模型的流式客户端，走 OpenAI 兼容接口，只用 JDK HttpClient，不引入额外 WebFlux 依赖。
 * 未配置密钥或模型时直接抛 TL-5031，页面据此提示稍后重试或走规则结果。
 * 模型配置页保存的值经 {@link AiSettingsPort} 读进来，每轮取一次，改完下一轮即生效。
 */
@Component
public class ArkChatClient implements ChatPort {

    private final String fallbackBaseUrl;
    private final String fallbackApiKey;
    private final String fallbackModel;
    private final double temperature;
    private final int maxTokens;
    private final int timeoutSeconds;
    private final ObjectProvider<AiSettingsPort> settingsPort;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;

    public ArkChatClient(@Value("${tracelink.ai.ark.base-url:https://ark.cn-beijing.volces.com/api/v3}") String baseUrl,
                         @Value("${tracelink.ai.ark.api-key:}") String apiKey,
                         @Value("${tracelink.ai.ark.chat.model:}") String model,
                         @Value("${tracelink.ai.ark.chat.temperature:0.2}") double temperature,
                         @Value("${tracelink.ai.ark.chat.max-tokens:2048}") int maxTokens,
                         @Value("${tracelink.ai.ark.chat.timeout-seconds:30}") int timeoutSeconds,
                         ObjectProvider<AiSettingsPort> settingsPort) {
        this.fallbackBaseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.fallbackApiKey = apiKey == null ? "" : apiKey.trim();
        this.fallbackModel = model == null ? "" : model.trim();
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.timeoutSeconds = Math.max(5, timeoutSeconds);
        this.settingsPort = settingsPort;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(10, this.timeoutSeconds)))
                .build();
    }

    /** 页面保存过的值优先，没保存过的项用启动时的环境变量 */
    private AiSettings settings() {
        AiSettingsPort port = settingsPort == null ? null : settingsPort.getIfAvailable();
        AiSettings configured = port == null ? null : port.current();
        if (configured == null) {
            return new AiSettings(fallbackBaseUrl, fallbackApiKey, fallbackModel);
        }
        return new AiSettings(
                configured.baseUrl() == null || configured.baseUrl().isBlank() ? fallbackBaseUrl : configured.baseUrl(),
                configured.apiKey() == null || configured.apiKey().isBlank() ? fallbackApiKey : configured.apiKey(),
                configured.chatModel() == null || configured.chatModel().isBlank() ? fallbackModel : configured.chatModel());
    }

    @Override
    public String modelName() {
        return settings().chatModel();
    }

    public boolean available() {
        return settings().configured();
    }

    /** 逐段回调回答增量；上游报错、鉴权失败或流中断统一抛 TL-5031 */
    public void stream(String systemPrompt, String userPrompt, Consumer<String> onDelta) {
        AiSettings settings = settings();
        if (!settings.configured()) {
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "未配置 Ark 密钥或对话模型，暂时不能生成回答");
        }
        String baseUrl = settings.normalizedBaseUrl();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", settings.chatModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        body.put("stream", true);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(Math.max(30, timeoutSeconds * 2L)))
                    .header("Authorization", "Bearer " + settings.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
        } catch (IOException e) {
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "模型请求构造失败：" + e.getMessage());
        }

        HttpResponse<InputStream> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "模型服务不可达：" + e.getMessage());
        }
        if (response.statusCode() != 200) {
            String detail = readSnippet(response.body());
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "模型服务返回 " + response.statusCode() + "：" + detail);
        }
        readStream(response.body(), onDelta);
    }

    private void readStream(InputStream in, Consumer<String> onDelta) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(data)) {
                    return;
                }
                JsonNode node = mapper.readTree(data);
                String delta = node.path("choices").path(0).path("delta").path("content").asText("");
                if (!delta.isEmpty()) {
                    onDelta.accept(delta);
                }
            }
        } catch (IOException e) {
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "模型流读取失败：" + e.getMessage());
        }
    }

    private String readSnippet(InputStream in) {
        try (InputStream body = in) {
            byte[] bytes = body.readNBytes(300);
            return new String(bytes, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
        } catch (IOException e) {
            return "无法读取响应";
        }
    }
}
