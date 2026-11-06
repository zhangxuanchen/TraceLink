package io.tracelink.api.web;

import io.tracelink.api.dto.ImportRowEditRequest;
import io.tracelink.api.security.AuthContext;
import io.tracelink.app.importing.DailyReportView;
import io.tracelink.app.importing.ImportAppService;
import io.tracelink.app.importing.ImportBatchView;
import io.tracelink.app.importing.ImportConfirmResult;
import io.tracelink.app.importing.ImportPreviewView;
import io.tracelink.app.importing.ImportRowView;
import io.tracelink.app.importing.RowEditCommand;
import io.tracelink.app.job.JobAppService;
import io.tracelink.common.api.ApiResult;
import io.tracelink.common.error.BizException;
import io.tracelink.domain.job.model.JobType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** IMP-1 到 IMP-4 与日报页查询（DESIGN 5.1） */
@RestController
@RequestMapping("/api/v1")
public class ImportController {

    private final ImportAppService importAppService;
    private final JobAppService jobAppService;

    public ImportController(ImportAppService importAppService, JobAppService jobAppService) {
        this.importAppService = importAppService;
        this.jobAppService = jobAppService;
    }

    @PostMapping("/imports")
    public ApiResult<ImportBatchView> upload(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw BizException.invalid("请选择要上传的 xlsx 文件");
        }
        try {
            return ApiResult.ok(importAppService.upload(AuthContext.current(), file.getOriginalFilename(),
                    file.getInputStream()));
        } catch (IOException e) {
            throw BizException.invalid("文件读取失败：" + e.getMessage());
        }
    }

    @GetMapping("/imports")
    public ApiResult<List<ImportBatchView>> list() {
        return ApiResult.ok(importAppService.listBatches(AuthContext.current()));
    }

    @GetMapping("/imports/{id}")
    public ApiResult<ImportBatchView> detail(@PathVariable Long id) {
        return ApiResult.ok(importAppService.batch(AuthContext.current(), id));
    }

    @GetMapping("/imports/{id}/preview")
    public ApiResult<ImportPreviewView> preview(@PathVariable Long id,
                                                @RequestParam(defaultValue = "false") boolean reanalyze) {
        return ApiResult.ok(importAppService.preview(AuthContext.current(), id, reanalyze));
    }

    @PutMapping("/imports/{id}/rows/{rowId}")
    public ApiResult<ImportRowView> updateRow(@PathVariable Long id, @PathVariable Long rowId,
                                              @RequestBody ImportRowEditRequest request) {
        return ApiResult.ok(importAppService.updateRow(AuthContext.current(), id, rowId,
                new RowEditCommand(request.title(), request.assigneeId(), request.deptId(),
                        request.planEnd(), request.action(), request.resultText(),
                        request.problemText(), request.solutionText())));
    }

    @PostMapping("/imports/{id}/confirm")
    public ApiResult<ImportConfirmResult> confirm(@PathVariable Long id) {
        return ApiResult.ok(jobAppService.runRecorded(JobType.PARSE_IMPORT, Map.of("batchId", id),
                AuthContext.current(), "导入确认 #" + id,
                () -> importAppService.confirm(AuthContext.current(), id)));
    }

    @GetMapping("/imports/{id}/report.xlsx")
    public ResponseEntity<byte[]> report(@PathVariable Long id) {
        ImportAppService.ReportFile file = importAppService.report(AuthContext.current(), id);
        return download(file.fileName(), file.content());
    }

    @DeleteMapping("/imports/{id}")
    public ApiResult<ImportBatchView> revoke(@PathVariable Long id) {
        return ApiResult.ok(importAppService.revoke(AuthContext.current(), id));
    }

    @GetMapping("/reports")
    public ApiResult<List<DailyReportView>> reports(@RequestParam(required = false) LocalDate from,
                                                    @RequestParam(required = false) LocalDate to,
                                                    @RequestParam(required = false) Long reporterId) {
        return ApiResult.ok(importAppService.reports(AuthContext.current(), from, to, reporterId));
    }

    static ResponseEntity<byte[]> download(String fileName, byte[] content) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(content.length)
                .body(content);
    }
}
