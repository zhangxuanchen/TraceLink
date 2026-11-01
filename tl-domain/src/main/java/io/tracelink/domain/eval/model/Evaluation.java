package io.tracelink.domain.eval.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评价。scores 与 evidence 以 JSON 字符串保存，evidence 里带工单与追踪记录 ID，
 * 页面据此下钻（DESIGN 4.8）。同一对象同一周期唯一，重复触发返回首次结果。
 */
public class Evaluation {

    public Long id;
    public EvaluationTargetType targetType;
    public Long targetId;
    public String period;
    public BigDecimal score;
    public String scores;
    public String evidence;
    public BigDecimal confidence;
    public Integer sampleCnt;
    public EvaluationStatus status;
    public LocalDateTime createdAt;
}
