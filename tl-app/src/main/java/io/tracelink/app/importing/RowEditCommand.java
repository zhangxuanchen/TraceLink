package io.tracelink.app.importing;

/** 预览页的人工校正项。只允许改标题、主责、部门、期限、动作与三段文本 */
public record RowEditCommand(String title, String assigneeId, String deptId, String planEnd, String action,
                             String resultText, String problemText, String solutionText) {
}
