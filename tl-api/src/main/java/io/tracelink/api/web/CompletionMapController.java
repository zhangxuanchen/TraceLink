package io.tracelink.api.web;

import io.tracelink.api.security.AuthContext;
import io.tracelink.app.gate.CompletionMapAppService;
import io.tracelink.app.gate.CompletionMapView;
import io.tracelink.app.gate.GapRankView;
import io.tracelink.app.gate.MatrixView;
import io.tracelink.app.gate.MatterMapView;
import io.tracelink.app.gate.NotificationView;
import io.tracelink.app.gate.SnapshotResult;
import io.tracelink.common.api.ApiResult;
import io.tracelink.common.api.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** CMP-1 到 CMP-4：单工单完成地图、部门缺口矩阵、缺口排行、事项地图，以及快照与提醒入口。 */
@RestController
@RequestMapping("/api/v1")
public class CompletionMapController {

    private final CompletionMapAppService completionMapAppService;

    public CompletionMapController(CompletionMapAppService completionMapAppService) {
        this.completionMapAppService = completionMapAppService;
    }

    @GetMapping("/tasks/{id}/completion-map")
    public ApiResult<CompletionMapView> map(@PathVariable Long id,
                                            @RequestParam(defaultValue = "true") boolean includeNa,
                                            @RequestParam(required = false) String gateCodes) {
        return ApiResult.ok(completionMapAppService.map(id, AuthContext.current(), includeNa, gateCodes));
    }

    @GetMapping("/completion-map/matrix")
    public ApiResult<MatrixView> matrix(@RequestParam(required = false) Long deptId,
                                        @RequestParam(required = false) String taskType,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "50") int pageSize) {
        return ApiResult.ok(completionMapAppService.matrix(AuthContext.current(), deptId, taskType,
                page, Math.min(Math.max(1, pageSize), 200)));
    }

    @GetMapping("/completion-map/gaps")
    public ApiResult<GapRankView> gaps(@RequestParam(required = false) Long deptId) {
        return ApiResult.ok(completionMapAppService.gaps(AuthContext.current(), deptId));
    }

    @GetMapping("/completion-map/matter/{taskId}")
    public ApiResult<MatterMapView> matter(@PathVariable Long taskId) {
        return ApiResult.ok(completionMapAppService.matter(taskId, AuthContext.current()));
    }

    /** 手动重算当日快照，供凌晨任务失败后补跑与验收使用 */
    @PostMapping("/completion-map/snapshot")
    public ApiResult<SnapshotResult> snapshot(@RequestParam(required = false) LocalDate date) {
        return ApiResult.ok(completionMapAppService.snapshot(AuthContext.current(), date));
    }

    @GetMapping("/notifications")
    public ApiResult<PageResult<NotificationView>> notifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResult.ok(completionMapAppService.notifications(AuthContext.current(), unreadOnly, limit));
    }

    @PostMapping("/notifications/{id}/read")
    public ApiResult<Boolean> read(@PathVariable Long id) {
        return ApiResult.ok(completionMapAppService.readNotification(id, AuthContext.current()));
    }
}
