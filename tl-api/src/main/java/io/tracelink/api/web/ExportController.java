package io.tracelink.api.web;

import io.tracelink.api.dto.ExportCreateRequest;
import io.tracelink.api.security.AuthContext;
import io.tracelink.app.exporting.ExportAppService;
import io.tracelink.app.exporting.ExportRequest;
import io.tracelink.app.exporting.ExportTaskView;
import io.tracelink.app.job.JobAppService;
import io.tracelink.app.job.JobView;
import io.tracelink.common.api.ApiResult;
import io.tracelink.common.error.BizException;
import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.exporting.model.ExportTemplate;
import io.tracelink.domain.job.model.JobType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** EXP-1、EXP-2：生成导出件并下载；async=true 时排队执行并返回任务单，关掉浏览器不丢进度 */
@RestController
@RequestMapping("/api/v1/exports")
public class ExportController {

    private final ExportAppService exportAppService;
    private final JobAppService jobAppService;

    public ExportController(ExportAppService exportAppService, JobAppService jobAppService) {
        this.exportAppService = exportAppService;
        this.jobAppService = jobAppService;
    }

    @PostMapping
    public ApiResult<Object> create(@RequestBody ExportCreateRequest request,
                                    @RequestParam(defaultValue = "false") boolean async) {
        ExportRequest command = new ExportRequest(
                template(request.template()), request.from(), request.to(), request.deptId(), request.deptIds(),
                request.employeeId(), request.reportDate(), request.remark(),
                request.keyword(), request.status(), request.type(),
                Boolean.TRUE.equals(request.overdueOnly()), Boolean.TRUE.equals(request.blockedOnly()),
                Boolean.TRUE.equals(request.mine()));
        AuthUser actor = AuthContext.current();
        if (!async) {
            return ApiResult.ok(exportAppService.create(actor, command));
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("template", command.template() == null ? null : command.template().name());
        payload.put("from", command.from() == null ? null : command.from().toString());
        payload.put("to", command.to() == null ? null : command.to().toString());
        payload.put("deptId", command.deptId());
        payload.put("deptIds", command.deptIds());
        payload.put("employeeId", command.employeeId());
        payload.put("reportDate", command.reportDate() == null ? null : command.reportDate().toString());
        payload.put("keyword", command.keyword());
        payload.put("status", command.status());
        payload.put("type", command.type());
        payload.put("overdueOnly", command.overdueOnly());
        payload.put("blockedOnly", command.blockedOnly());
        payload.put("mine", command.mine());
        JobView job = jobAppService.submitAsync(JobType.EXPORT, payload, actor,
                "导出 " + command.template().label(), () -> {
                    ExportTaskView view = exportAppService.create(actor, command);
                    return "exportId=" + view.id() + " file=" + view.fileName();
                });
        return ApiResult.ok(job);
    }

    @GetMapping
    public ApiResult<List<ExportTaskView>> list() {
        return ApiResult.ok(exportAppService.list(AuthContext.current()));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        ExportAppService.DownloadFile file = exportAppService.download(AuthContext.current(), id);
        return ImportController.download(file.fileName(), file.content());
    }

    private ExportTemplate template(String code) {
        if (code == null || code.isBlank()) {
            return ExportTemplate.DAILY;
        }
        try {
            return ExportTemplate.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BizException.invalid("未知导出模板：" + code + "，可选 DAILY/WEEKLY/LEDGER/PERF/CHART");
        }
    }
}
