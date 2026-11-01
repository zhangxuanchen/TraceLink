package io.tracelink.app.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/** JSON 列与审计快照的读写。审计快照里的值先转成字符串，避免依赖 Java 时间模块 */
final class TaskJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TaskJson() {
    }

    static String array(List<String> values) {
        try {
            return MAPPER.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception e) {
            throw new IllegalStateException("验收条件序列化失败", e);
        }
    }

    static List<String> strings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    static String snapshot(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            return null;
        }
    }
}
