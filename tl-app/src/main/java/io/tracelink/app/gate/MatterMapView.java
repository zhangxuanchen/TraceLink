package io.tracelink.app.gate;

import java.util.List;

/** CMP-4：事项完成地图。父子单与复发链合成一件事，看每次停在哪一步。 */
public record MatterMapView(
        Long rootTaskId,
        String matterTitle,
        List<MatterTask> tasks,
        String stuckGateCode,
        String stuckGateName,
        int stuckCount) {

    public record MatterTask(
            Long taskId,
            String taskNo,
            String title,
            String status,
            String statusLabel,
            String assigneeName,
            String createdAt,
            String planEnd,
            String actualEnd,
            int completionRate,
            List<GateItem> gates) {
    }
}
