package io.tracelink.domain.ai.repository;

import io.tracelink.domain.ai.model.Suggestion;
import io.tracelink.domain.ai.model.SuggestionStatus;
import io.tracelink.domain.ai.model.SuggestionTargetType;
import io.tracelink.domain.ai.model.SuggestionType;

import java.time.LocalDateTime;
import java.util.List;

public interface SuggestionRepository {

    Suggestion findById(Long id);

    List<Suggestion> list(SuggestionType type, SuggestionTargetType targetType,
                          SuggestionStatus status, int limit);

    /** 同一对象同一类型只保留一条待确认建议，避免每轮生成都堆重复项 */
    boolean existsPending(SuggestionType type, SuggestionTargetType targetType, Long targetId);

    void insert(Suggestion suggestion);

    void accept(Long id, Long acceptedBy, LocalDateTime acceptedAt, String acceptResult);
}
