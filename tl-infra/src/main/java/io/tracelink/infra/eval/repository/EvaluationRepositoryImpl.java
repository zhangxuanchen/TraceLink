package io.tracelink.infra.eval.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.tracelink.domain.eval.model.Evaluation;
import io.tracelink.domain.eval.model.EvaluationStatus;
import io.tracelink.domain.eval.model.EvaluationTargetType;
import io.tracelink.domain.eval.repository.EvaluationRepository;
import io.tracelink.infra.eval.entity.EvaluationEntity;
import io.tracelink.infra.eval.mapper.EvaluationMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class EvaluationRepositoryImpl implements EvaluationRepository {

    private final EvaluationMapper mapper;

    public EvaluationRepositoryImpl(EvaluationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Evaluation findById(Long id) {
        EvaluationEntity entity = mapper.selectById(id);
        return entity == null ? null : toModel(entity);
    }

    @Override
    public Evaluation find(EvaluationTargetType targetType, Long targetId, String period) {
        List<EvaluationEntity> rows = mapper.selectList(new QueryWrapper<EvaluationEntity>()
                .eq("target_type", targetType.name())
                .eq("target_id", targetId)
                .eq("period", period)
                .last("LIMIT 1"));
        return rows.isEmpty() ? null : toModel(rows.get(0));
    }

    @Override
    public List<Evaluation> list(String period, EvaluationTargetType targetType, Long targetId, int limit) {
        QueryWrapper<EvaluationEntity> wrapper = new QueryWrapper<EvaluationEntity>()
                .orderByDesc("created_at")
                .orderByDesc("id")
                .last("LIMIT " + Math.max(1, Math.min(limit, 200)));
        if (period != null && !period.isBlank()) {
            wrapper.eq("period", period.trim());
        }
        if (targetType != null) {
            wrapper.eq("target_type", targetType.name());
        }
        if (targetId != null) {
            wrapper.eq("target_id", targetId);
        }
        return mapper.selectList(wrapper).stream().map(EvaluationRepositoryImpl::toModel).toList();
    }

    @Override
    public void insert(Evaluation evaluation) {
        EvaluationEntity entity = new EvaluationEntity();
        entity.targetType = evaluation.targetType;
        entity.targetId = evaluation.targetId;
        entity.period = evaluation.period;
        entity.score = evaluation.score;
        entity.scores = evaluation.scores;
        entity.evidence = evaluation.evidence;
        entity.confidence = evaluation.confidence;
        entity.sampleCnt = evaluation.sampleCnt;
        entity.status = evaluation.status == null ? EvaluationStatus.PUBLISHED : evaluation.status;
        entity.createdAt = evaluation.createdAt == null ? LocalDateTime.now() : evaluation.createdAt;
        mapper.insert(entity);
        evaluation.id = entity.id;
        evaluation.createdAt = entity.createdAt;
    }

    @Override
    public void updateStatus(Long id, EvaluationStatus status) {
        mapper.update(null, new UpdateWrapper<EvaluationEntity>()
                .eq("id", id)
                .set("status", status.name()));
    }

    @Override
    public void updateAppeal(Long id, EvaluationStatus status, String evidence) {
        mapper.update(null, new UpdateWrapper<EvaluationEntity>()
                .eq("id", id)
                .set("status", status.name())
                .set("evidence", evidence));
    }

    public static Evaluation toModel(EvaluationEntity e) {
        Evaluation v = new Evaluation();
        v.id = e.id;
        v.targetType = e.targetType;
        v.targetId = e.targetId;
        v.period = e.period;
        v.score = e.score;
        v.scores = e.scores;
        v.evidence = e.evidence;
        v.confidence = e.confidence;
        v.sampleCnt = e.sampleCnt;
        v.status = e.status;
        v.createdAt = e.createdAt;
        return v;
    }
}
