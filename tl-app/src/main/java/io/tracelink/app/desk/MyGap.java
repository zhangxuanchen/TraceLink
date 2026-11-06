package io.tracelink.app.desk;

/** 我名下的未达成条件，点「去处理」回到工单详情对应位置 */
public record MyGap(
        Long taskId,
        String taskNo,
        String title,
        String gateCode,
        String gateLabel,
        int gapDays,
        String hint,
        String actionUrl) {
}
