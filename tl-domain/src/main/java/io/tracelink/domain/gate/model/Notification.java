package io.tracelink.domain.gate.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 缺口提醒。level 取 NEW 缺口刚出现 / FIRST 滞留三日 / SECOND 滞留七日。
 * episodeStart 是同一段缺口的起点，唯一键为工单加条件加级别加起点，保证一段缺口每级只提醒一次。
 */
public class Notification {

    public Long id;
    public Long recipientId;
    public String type;
    public Long taskId;
    public String taskNo;
    public GateCode gateCode;
    public String level;
    public Integer gapDays;
    public LocalDate episodeStart;
    public String title;
    public String content;
    public Boolean readFlag;
    public LocalDateTime createdAt;
}
