package io.tracelink.app.task;

import java.time.LocalDateTime;

/** 写操作的返回体，字段与 DESIGN 5.2 的成功响应一致 */
public final class TaskOperations {

    private TaskOperations() {
    }

    public record CreateResult(Long taskId, String taskNo, String status, LocalDateTime createdAt) {
    }

    public record CheckinResult(Long checkinId, String taskStatus, int progress, String closureLevel) {
    }

    public record VerifyResult(String taskStatus, LocalDateTime actualEnd, int rejectCount, int delayCount) {
    }

    public record UrgeResult(int urgeCount, String message) {
    }
}
