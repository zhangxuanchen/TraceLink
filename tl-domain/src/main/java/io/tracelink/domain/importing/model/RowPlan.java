package io.tracelink.domain.importing.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 一行日报解析后的建单计划。以 JSON 存在 import_row.parsed，
 * 确认阶段原样读回，保证预览看到的内容与最终入库完全一致。
 */
public class RowPlan {

    public String fingerprint;
    public String sheet;
    public int rowNo;

    /** 来源页：汇报人、部门与日期 */
    public Long reporterId;
    public String reporterName;
    public Long deptId;
    public String deptName;
    public LocalDate reportDate;

    /** 工单主体 */
    public String title;
    public String description;
    /** 负责人列的原始文本：导出日报时要原样写回，回导才能认回同一个责任人 */
    public String ownerText;
    public String assigneeText;
    public Long assigneeId;
    public String assigneeName;
    public List<Long> participantIds = new ArrayList<>();
    public List<String> participantNames = new ArrayList<>();
    public LocalDate planStart;
    public LocalDate planEnd;
    public String timeText;
    public String acceptCriteria;

    /** 追踪证据 */
    public String problemText;
    public String solutionText;
    public String resultText;
    public String remark;

    /** 归并与校验 */
    public RowAction action = RowAction.CREATE;
    public Long mergeTaskId;
    public String mergeTaskNo;
    public String mergeReason;
    public double similarity;
    public List<String> issues = new ArrayList<>();

    public boolean buildable() {
        return action != RowAction.SKIP && title != null && !title.isBlank();
    }
}
