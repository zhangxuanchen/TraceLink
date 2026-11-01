package io.tracelink.api.web;

import io.tracelink.api.dto.ExportCreateRequest;
import io.tracelink.api.security.AuthContext;
import io.tracelink.app.exporting.ExportAppService;
import io.tracelink.app.exporting.ExportRequest;
import io.tracelink.app.exporting.ExportTaskView;
import io.tracelink.common.api.ApiResult;
import io.tracelink.common.error.BizException;
import io.tracelink.domain.exporting.model.ExportTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** EXP-1、EXP-2：生成导出件并下载 */
@RestController
@RequestMapping("/api/v1/exports")
public class ExportController {

    private final ExportAppService exportAppService;

    public ExportController(ExportAppService exportAppService) {
        this.exportAppService = exportAppService;
    }

    @PostMapping
    public ApiResult<ExportTaskView> create(@RequestBody ExportCreateRequest request) {
        return ApiResult.ok(exportAppService.create(AuthContext.current(), new ExportRequest(
                template(request.template()), request.from(), request.to(), request.deptId(),
                request.employeeId(), request.reportDate(), request.remark())));
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
