package io.tracelink.domain.ai.model;

import java.time.LocalDateTime;

/**
 * AI 建议。payload 存建议正文与可执行动作，evidence 存依据；采纳人、时间与动作对象
 * 写在 accepted_* 字段上，另有一条审计记录（DESIGN 4.8、TC-M5-08）。
 */
public class Suggestion {

    public Long id;
    public SuggestionType type;
    public SuggestionTargetType targetType;
    public Long targetId;
    public String payload;
    public String evidence;
    public SuggestionStatus status;
    public String modelName;
    public String promptVer;
    public Integer tokenIn;
    public Integer tokenOut;
    public Integer latencyMs;
    public Long acceptedBy;
    public LocalDateTime acceptedAt;
    public String acceptResult;
    public LocalDateTime createdAt;
}
