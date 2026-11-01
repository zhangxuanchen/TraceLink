package io.tracelink.app.task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 工单详情：列表行加问题、措施、参与人与提交前的缺口清单 */
public record TaskDetailView(
        TaskView task,
        String description,
        List<String> acceptCriteria,
        String resultSummary,
        List<IssueView> issues,
        List<ActionView> actions,
        List<AssigneeView> assignees,
        List<String> submitGaps) {

    public record IssueView(
            Long id,
            Integer seq,
            String content,
            String category,
            Integer severity,
            String status,
            String statusLabel,
            LocalDateTime closedAt) {
    }

    public record ActionView(
            Long id,
            Long issueId,
            Integer seq,
            String content,
            Long ownerId,
            String ownerName,
            LocalDate dueDate,
            String status,
            String statusLabel,
            boolean overdue) {
    }

    public record AssigneeView(Long employeeId, String name, String roleType, String roleLabel) {
    }
}
