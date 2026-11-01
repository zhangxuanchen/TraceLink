package io.tracelink.infra.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.tracelink.domain.ai.model.SuggestionStatus;
import io.tracelink.domain.ai.model.SuggestionTargetType;
import io.tracelink.domain.ai.model.SuggestionType;

import java.time.LocalDateTime;

@TableName("ai_suggestion")
public class SuggestionEntity {

    @TableId(type = IdType.AUTO)
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
