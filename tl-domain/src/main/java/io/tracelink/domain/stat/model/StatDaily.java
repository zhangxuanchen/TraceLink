package io.tracelink.domain.stat.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 统计日预聚合。(日期, 维度, 编码, 指标) 唯一，凌晨重算可重复覆盖 */
public class StatDaily {

    public Long id;
    public LocalDate statDate;
    public StatDimType dimType;
    public String dimCode;
    public String metricCode;
    public BigDecimal value;
    public LocalDateTime updatedAt;

    public static StatDaily of(LocalDate date, StatDimType dimType, String dimCode,
                               String metricCode, double value) {
        StatDaily s = new StatDaily();
        s.statDate = date;
        s.dimType = dimType;
        s.dimCode = dimCode;
        s.metricCode = metricCode;
        s.value = BigDecimal.valueOf(value);
        return s;
    }
}
