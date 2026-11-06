package io.tracelink.api.web;

import io.tracelink.api.security.AuthContext;
import io.tracelink.app.gate.CompletionMapAppService;
import io.tracelink.app.gate.CompletionMapView;
import io.tracelink.app.gate.GapRankView;
import io.tracelink.app.gate.MatrixView;
import io.tracelink.app.gate.MatterMapView;
import io.tracelink.app.gate.NotificationView;
import io.tracelink.app.gate.SnapshotResult;
import io.tracelink.app.job.JobAppService;
import io.tracelink.common.api.ApiResult;
import io.tracelink.common.api.PageResult;
import io.tracelink.domain.job.model.JobType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/** CMP-1 到 CMP-4：单工单完成地图、部门缺口矩阵、缺口排行、事项地图，以及快照与提醒入口。 */
@RestController
@RequestMapping("/api/v1")
public class CompletionMapController {

    private final CompletionMapAppService completionMapAppService;
    private final JobAppService jobAppService;

    public CompletionMapController(CompletionMapAppService completionMapAppService,
                                   JobAppService jobAppService) {
        this.completionMapAppService = completionMapAppService;
        this.jobAppService = jobAppService;
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

    /** 手动重算当日快照，供凌晨任务失败后补跑与验收使用；每次重算都落一条任务记录 */
    @PostMapping("/completion-map/snapshot")
    public ApiResult<SnapshotResult> snapshot(@RequestParam(required = false) LocalDate date) {
        Map<String, Object> payload = new HashMap<>();
        if (date != null) {
            payload.put("date", date.toString());
        }
        return ApiResult.ok(jobAppService.runRecorded(JobType.GATE_SNAPSHOT, payload,
                AuthContext.current(), "完成地图快照" + (date == null ? "" : " " + date),
                () -> completionMapAppService.snapshot(AuthContext.current(), date)));
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
