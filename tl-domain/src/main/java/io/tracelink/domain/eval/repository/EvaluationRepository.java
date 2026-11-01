package io.tracelink.domain.eval.repository;

import io.tracelink.domain.eval.model.Evaluation;
import io.tracelink.domain.eval.model.EvaluationStatus;
import io.tracelink.domain.eval.model.EvaluationTargetType;

import java.util.List;

public interface EvaluationRepository {

    Evaluation findById(Long id);

    /** 周期唯一键：同对象同周期只保留首次结果 */
    Evaluation find(EvaluationTargetType targetType, Long targetId, String period);

    List<Evaluation> list(String period, EvaluationTargetType targetType, Long targetId, int limit);

    void insert(Evaluation evaluation);

    void updateStatus(Long id, EvaluationStatus status);

    /** 申诉：状态与证据一起更新，理由写进 evidence 便于复核 */
    void updateAppeal(Long id, EvaluationStatus status, String evidence);
}
