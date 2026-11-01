package io.tracelink.domain.stat.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 业务指标：由日报生产块等非工单来源写入，与 stat_daily 分开存细节 */
public class TaskMetric {

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
