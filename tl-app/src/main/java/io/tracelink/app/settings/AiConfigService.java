package io.tracelink.app.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.admin.model.SysConfigItem;
import io.tracelink.domain.admin.repository.AdminSettingsRepository;
import io.tracelink.domain.ai.AiSettings;
import io.tracelink.domain.ai.AiSettingsPort;
import io.tracelink.domain.audit.model.AuditLog;
import io.tracelink.domain.audit.repository.AuditLogRepository;
import io.tracelink.domain.auth.AuthUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 模型配置页的应用服务：页面上保存的火山 Ark 密钥与模型标识写 sys_config，
 * 智能层每轮经 {@link AiSettingsPort} 读一次，保存后下一轮即生效，不需要重启进程。
 * 页面只回显掩码，密钥明文不出接口。
 */
@Service
public class AiConfigService implements AiSettingsPort {

    public static final String KEY_API_KEY = "ai.ark.api-key";
    public static final String KEY_CHAT_MODEL = "ai.ark.chat.model";
    public static final String KEY_BASE_URL = "ai.ark.base-url";

    private static final String ROLE_ADMIN = "SYS_ADMIN";
    private static final String ROLE_MANAGER = "MANAGER";
    private static final String SOURCE_PAGE = "页面配置";
    private static final String SOURCE_ENV = "环境变量";
    private static final String SOURCE_NONE = "未配置";

    private final AdminSettingsRepository settingsRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final String envBaseUrl;
    private final String envApiKey;
    private final String envChatModel;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public AiConfigService(AdminSettingsRepository settingsRepository,
                           AuditLogRepository auditLogRepository,
                           ObjectMapper objectMapper,
                           @Value("${tracelink.ai.ark.base-url:https://ark.cn-beijing.volces.com/api/v3}") String envBaseUrl,
                           @Value("${tracelink.ai.ark.api-key:}") String envApiKey,
                           @Value("${tracelink.ai.ark.chat.model:}") String envChatModel) {
        this.settingsRepository = settingsRepository;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.envBaseUrl = envBaseUrl == null ? "" : envBaseUrl.trim();
        this.envApiKey = envApiKey == null ? "" : envApiKey.trim();
        this.envChatModel = envChatModel == null ? "" : envChatModel.trim();
    }

    // ---------------------------------------------------------------- 读取

    @Override
    public AiSettings current() {
        Map<String, String> stored = storedValues(configItems());
        return new AiSettings(
                pick(stored.get(KEY_BASE_URL), envBaseUrl),
                pick(stored.get(KEY_API_KEY), envApiKey),
                pick(stored.get(KEY_CHAT_MODEL), envChatModel));
    }

    public AiConfigView view(AuthUser actor) {
        requireRead(actor);
        List<SysConfigItem> items = configItems();
        Map<String, String> stored = storedValues(items);
        AiSettings settings = current();
        boolean keyConfigured = !settings.apiKey().isBlank();
        boolean fromPage = isFilled(stored.get(KEY_API_KEY))
                || isFilled(stored.get(KEY_CHAT_MODEL))
                || isFilled(stored.get(KEY_BASE_URL));
        return new AiConfigView(
                settings.normalizedBaseUrl(),
                settings.chatModel(),
                mask(settings.apiKey()),
                keyConfigured,
                settings.configured(),
                fromPage ? SOURCE_PAGE : (keyConfigured ? SOURCE_ENV : SOURCE_NONE),
                lastUpdatedAt(items),
                hasRole(actor, ROLE_ADMIN));
    }

    // ---------------------------------------------------------------- 保存

    @Transactional
    public AiConfigView update(AuthUser actor, String baseUrl, String chatModel, String apiKey) {
        requireAdmin(actor);
        AiSettings before = current();
        String normalizedBase = validateBaseUrl(baseUrl);
        String normalizedModel = requireText(chatModel, "请填写火山方舟的模型标识或接入点 ID");
        String normalizedKey = apiKey == null ? "" : apiKey.trim();
        if (!normalizedKey.isEmpty() && normalizedKey.length() < 8) {
            throw BizException.invalid("API Key 长度不对，请从火山方舟控制台整段复制");
        }

        Map<String, String> stored = storedValues(configItems());
        boolean changed = false;
        changed |= writeIfChanged(KEY_BASE_URL, normalizedBase, stored.get(KEY_BASE_URL), actor);
        changed |= writeIfChanged(KEY_CHAT_MODEL, normalizedModel, stored.get(KEY_CHAT_MODEL), actor);
        if (!normalizedKey.isEmpty()) {
            changed |= writeIfChanged(KEY_API_KEY, normalizedKey, stored.get(KEY_API_KEY), actor);
        }
        if (changed) {
            audit(actor, before, current());
        }
        return view(actor);
    }

    // ---------------------------------------------------------------- 连通性自检

    /** 用当前生效的配置打一次最小对话请求；密钥不出接口，只回状态码、耗时与返回片段 */
    public AiTestResult test(AuthUser actor) {
        requireRead(actor);
        AiSettings settings = current();
        if (!settings.configured()) {
            return new AiTestResult(false, 0, 0, settings.chatModel(), "还没有配置 API Key 或模型 ID");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", settings.chatModel());
        body.put("messages", List.of(Map.of("role", "user", "content", "ping")));
        body.put("max_tokens", 8);
        body.put("stream", false);

        long start = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(settings.normalizedBaseUrl() + "/chat/completions"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + settings.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;
            int status = response.statusCode();
            String snippet = snippet(response.body());
            if (status != 200) {
                return new AiTestResult(false, status, latency, settings.chatModel(),
                        "模型服务返回 " + status + "：" + snippet);
            }
            JsonNode json = objectMapper.readTree(response.body());
            boolean hasChoice = json.path("choices").isArray() && !json.path("choices").isEmpty();
            return new AiTestResult(hasChoice, status, latency, settings.chatModel(),
                    hasChoice ? "连接正常，tokens " + tokens(json) : "返回结构里没有 choices：" + snippet);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AiTestResult(false, 0, System.currentTimeMillis() - start, settings.chatModel(), "自检被中断");
        } catch (Exception e) {
            return new AiTestResult(false, 0, System.currentTimeMillis() - start, settings.chatModel(),
                    "自检失败：" + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 内部

    private List<SysConfigItem> configItems() {
        return settingsRepository.listConfigs();
    }

    private static Map<String, String> storedValues(List<SysConfigItem> items) {
        Map<String, String> values = new LinkedHashMap<>();
        for (SysConfigItem item : items) {
            if (item.cfgKey() != null) {
                values.put(item.cfgKey(), item.cfgValue());
            }
        }
        return values;
    }

    private static String pick(String stored, String fallback) {
        return isFilled(stored) ? stored.trim() : (fallback == null ? "" : fallback.trim());
    }

    private static boolean isFilled(String value) {
        return value != null && !value.isBlank();
    }

    private static String mask(String apiKey) {
        if (!isFilled(apiKey)) {
            return "";
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "****" + trimmed.substring(trimmed.length() - 4);
    }

    private static String lastUpdatedAt(List<SysConfigItem> items) {
        return items.stream()
                .filter(item -> item.cfgKey() != null && item.cfgKey().startsWith("ai.ark."))
                .map(SysConfigItem::updatedAt)
                .filter(Objects::nonNull)
                .max(String::compareTo)
                .orElse(null);
    }

    private boolean writeIfChanged(String key, String value, String stored, AuthUser actor) {
        if (Objects.equals(value, stored == null ? null : stored.trim())) {
            return false;
        }
        settingsRepository.updateConfig(key, value, actor.employeeId());
        return true;
    }

    private static String validateBaseUrl(String raw) {
        String value = requireText(raw, "请填写接口地址");
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            throw BizException.invalid("接口地址要以 http:// 或 https:// 开头");
        }
        return value;
    }

    private static String requireText(String raw, String message) {
        if (raw == null || raw.isBlank()) {
            throw BizException.invalid(message);
        }
        return raw.trim();
    }

    private static String snippet(String body) {
        if (body == null) {
            return "无响应体";
        }
        String flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() > 200 ? flat.substring(0, 200) + "…" : flat;
    }

    private static long tokens(JsonNode json) {
        return json.path("usage").path("total_tokens").asLong(0);
    }

    private void requireRead(AuthUser actor) {
        if (!hasRole(actor, ROLE_ADMIN) && !hasRole(actor, ROLE_MANAGER)) {
            throw new BizException(ErrorCode.NO_PERMISSION, "模型配置仅管理者可见");
        }
    }

    private void requireAdmin(AuthUser actor) {
        if (!hasRole(actor, ROLE_ADMIN)) {
            throw new BizException(ErrorCode.NO_PERMISSION, "只有系统管理员可以修改模型配置");
        }
    }

    private static boolean hasRole(AuthUser actor, String role) {
        return actor != null && actor.roles() != null && actor.roles().contains(role);
    }

    /** 审计里不留密钥原文，只记「改了哪几项」与掩码 */
    private void audit(AuthUser actor, AiSettings before, AiSettings after) {
        try {
            AuditLog log = new AuditLog();
            log.actorId = actor.employeeId();
            log.action = "UPDATE";
            log.bizType = "SYS_CONFIG";
            log.bizId = 0L;
            log.before = objectMapper.writeValueAsString(new LinkedHashMap<>(Map.of(
                    "model", before.chatModel(),
                    "baseUrl", before.normalizedBaseUrl(),
                    "apiKey", mask(before.apiKey()))));
            log.after = objectMapper.writeValueAsString(new LinkedHashMap<>(Map.of(
                    "model", after.chatModel(),
                    "baseUrl", after.normalizedBaseUrl(),
                    "apiKey", mask(after.apiKey()))));
            auditLogRepository.insert(log);
        } catch (Exception e) {
            // 审计失败不阻断配置保存
        }
    }

    public record AiConfigView(
            String baseUrl,
            String chatModel,
            String apiKeyMasked,
            boolean apiKeyConfigured,
            boolean configured,
            String source,
            String updatedAt,
            boolean editable) {
    }

    public record AiTestResult(boolean ok, int status, long latencyMs, String model, String message) {
    }
}
