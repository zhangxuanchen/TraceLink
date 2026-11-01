package io.tracelink.app.importing;

import java.util.List;

public record ImportRowView(Long id, String sheet, int rowNo, String level, String levelLabel,
                            String title, String reporterName, String reportDate,
                            String assigneeText, Long assigneeId, String assigneeName, List<String> participantNames,
                            String action, String actionLabel, Long mergeTaskId, String mergeTaskNo,
                            String mergeReason, List<String> issues,
                            String problemText, String solutionText, String resultText,
                            String planEnd, String timeText, String fingerprint, Long taskId) {
}
