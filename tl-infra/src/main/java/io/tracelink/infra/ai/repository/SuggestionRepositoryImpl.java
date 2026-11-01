package io.tracelink.infra.ai.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.tracelink.domain.ai.model.Suggestion;
import io.tracelink.domain.ai.model.SuggestionStatus;
import io.tracelink.domain.ai.model.SuggestionTargetType;
import io.tracelink.domain.ai.model.SuggestionType;
import io.tracelink.domain.ai.repository.SuggestionRepository;
import io.tracelink.infra.ai.entity.SuggestionEntity;
import io.tracelink.infra.ai.mapper.SuggestionMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class SuggestionRepositoryImpl implements SuggestionRepository {

    private final SuggestionMapper mapper;

    public SuggestionRepositoryImpl(SuggestionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Suggestion findById(Long id) {
        SuggestionEntity entity = mapper.selectById(id);
        return entity == null ? null : toModel(entity);
    }

    @Override
    public List<Suggestion> list(SuggestionType type, SuggestionTargetType targetType,
                                 SuggestionStatus status, int limit) {
        QueryWrapper<SuggestionEntity> wrapper = new QueryWrapper<SuggestionEntity>()
                .orderByDesc("created_at")
                .orderByDesc("id")
                .last("LIMIT " + Math.max(1, Math.min(limit, 200)));
        if (type != null) {
            wrapper.eq("type", type.name());
        }
        if (targetType != null) {
            wrapper.eq("target_type", targetType.name());
        }
        if (status != null) {
            wrapper.eq("status", status.name());
        }
        return mapper.selectList(wrapper).stream().map(SuggestionRepositoryImpl::toModel).toList();
    }

    @Override
    public boolean existsPending(SuggestionType type, SuggestionTargetType targetType, Long targetId) {
        return mapper.selectCount(new QueryWrapper<SuggestionEntity>()
                .eq("type", type.name())
                .eq("target_type", targetType.name())
                .eq("target_id", targetId)
                .eq("status", SuggestionStatus.PENDING.name())) > 0;
    }

    @Override
    public void insert(Suggestion suggestion) {
        SuggestionEntity entity = new SuggestionEntity();
        entity.type = suggestion.type;
        entity.targetType = suggestion.targetType;
        entity.targetId = suggestion.targetId;
        entity.payload = suggestion.payload;
        entity.evidence = suggestion.evidence;
        entity.status = suggestion.status == null ? SuggestionStatus.PENDING : suggestion.status;
        entity.modelName = suggestion.modelName;
        entity.promptVer = suggestion.promptVer;
        entity.tokenIn = suggestion.tokenIn;
        entity.tokenOut = suggestion.tokenOut;
        entity.latencyMs = suggestion.latencyMs;
        entity.createdAt = suggestion.createdAt == null ? LocalDateTime.now() : suggestion.createdAt;
        mapper.insert(entity);
        suggestion.id = entity.id;
        suggestion.createdAt = entity.createdAt;
    }

    @Override
    public void accept(Long id, Long acceptedBy, LocalDateTime acceptedAt, String acceptResult) {
        mapper.update(null, new UpdateWrapper<SuggestionEntity>()
                .eq("id", id)
                .set("status", SuggestionStatus.ACCEPTED.name())
                .set("accepted_by", acceptedBy)
                .set("accepted_at", acceptedAt)
                .set("accept_result", acceptResult));
    }

    public static Suggestion toModel(SuggestionEntity e) {
        Suggestion s = new Suggestion();
        s.id = e.id;
        s.type = e.type;
        s.targetType = e.targetType;
        s.targetId = e.targetId;
        s.payload = e.payload;
        s.evidence = e.evidence;
        s.status = e.status;
        s.modelName = e.modelName;
        s.promptVer = e.promptVer;
        s.tokenIn = e.tokenIn;
        s.tokenOut = e.tokenOut;
        s.latencyMs = e.latencyMs;
        s.acceptedBy = e.acceptedBy;
        s.acceptedAt = e.acceptedAt;
        s.acceptResult = e.acceptResult;
        s.createdAt = e.createdAt;
        return s;
    }
}
