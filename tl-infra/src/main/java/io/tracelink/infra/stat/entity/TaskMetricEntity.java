package io.tracelink.infra.stat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.tracelink.domain.stat.model.StatDimType;

import java.math.BigDecimal;
import java.time.LocalDate;

@TableName("task_metric")
public class TaskMetricEntity {

    @TableId(type = IdType.AUTO)
    public Long id;
    public Long taskId;
    public LocalDate statDate;
    public StatDimType dimType;
    public String dimCode;
    public String metricCode;
    public BigDecimal value;
    public String unit;
    public String extra;
}
