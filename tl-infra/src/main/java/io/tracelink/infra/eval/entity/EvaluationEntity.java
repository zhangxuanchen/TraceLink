package io.tracelink.infra.eval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.tracelink.domain.eval.model.EvaluationStatus;
import io.tracelink.domain.eval.model.EvaluationTargetType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("evaluation")
public class EvaluationEntity {

    @TableId(type = IdType.AUTO)
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
