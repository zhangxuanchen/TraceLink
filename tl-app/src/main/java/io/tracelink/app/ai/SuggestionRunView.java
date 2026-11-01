package io.tracelink.app.ai;

import java.util.List;
import java.util.Map;

/** 一次建议生成的结果：已存在待确认建议的计入 skipped */
public record SuggestionRunView(
        int created,
        int skipped,
        Map<String, Integer> byType,
        List<String> notes) {
}
