package io.tracelink.api.web;

import io.tracelink.api.security.AuthContext;
import io.tracelink.app.audit.AuditAppService;
import io.tracelink.app.audit.AuditLogView;
import io.tracelink.common.api.ApiResult;
import io.tracelink.common.api.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** SYS-2 审计日志查询，只读；写入口在各业务动作内部 */
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditController {

    private final AuditAppService auditAppService;

    public AuditController(AuditAppService auditAppService) {
        this.auditAppService = auditAppService;
    }

    @GetMapping
    public ApiResult<PageResult<AuditLogView>> page(@RequestParam(required = false) String bizType,
                                                    @RequestParam(required = false) Long bizId,
                                                    @RequestParam(required = false) Long actorId,
                                                    @RequestParam(required = false) String action,
                                                    @RequestParam(required = false) LocalDate from,
                                                    @RequestParam(required = false) LocalDate to,
                                                    @RequestParam(defaultValue = "1") int pageNo,
                                                    @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResult.ok(auditAppService.page(AuthContext.current(), bizType, bizId, actorId,
                action, from, to, pageNo, pageSize));
    }
}
