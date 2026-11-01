package io.tracelink.infra.stat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.tracelink.domain.stat.model.StatDimType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("stat_daily")
public class StatDailyEntity {

    @TableId(type = IdType.AUTO)
    public Long id;
    public LocalDate statDate;
    public StatDimType dimType;
    public String dimCode;
    public String metricCode;
    public BigDecimal value;
    public LocalDateTime updatedAt;
}
