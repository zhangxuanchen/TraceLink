package io.tracelink.app.gate;

import java.util.List;

/** CMP-1：单工单完成地图。实时算式，不读快照。 */
public record CompletionMapView(
        Long taskId,
        String taskNo,
        String title,
        String status,
        String statusLabel,
        int completionRate,
        List<GateItem> gates,
        String snapshotAt,
        String dataMode) {
}
