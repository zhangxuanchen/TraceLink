package io.tracelink.infra.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("daily_report")
public class DailyReportEntity {

    @TableId(type = IdType.AUTO)
    public Long id;
    public LocalDate reportDate;
    public Long reporterId;
    public String sourceSheet;
    public Long importBatchId;
    public String raw;
    public LocalDateTime createdAt;
}
