package io.tracelink.api.web;

import io.tracelink.api.dto.EvaluationAppealRequest;
import io.tracelink.api.dto.EvaluationRunRequest;
import io.tracelink.api.security.AuthContext;
import io.tracelink.app.eval.EvaluationAppService;
import io.tracelink.app.eval.EvaluationRunView;
import io.tracelink.app.eval.EvaluationView;
import io.tracelink.common.api.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** EVL-1/EVL-2：人员与任务评价生成、列表、证据下钻与申诉（仅管理者） */
@RestController
@RequestMapping("/api/v1/evaluations")
public class EvaluationController {

    private final EvaluationAppService evaluationAppService;

    public EvaluationController(EvaluationAppService evaluationAppService) {
        this.evaluationAppService = evaluationAppService;
    }

    @PostMapping("/run")
    public ApiResult<EvaluationRunView> run(@RequestBody(required = false) EvaluationRunRequest request) {
        EvaluationRunRequest body = request == null ? new EvaluationRunRequest(null, null) : request;
        return ApiResult.ok(evaluationAppService.run(AuthContext.current(), body.period(), body.targetType()));
    }

    @GetMapping
    public ApiResult<List<EvaluationView>> list(@RequestParam(required = false) String period,
                                                @RequestParam(required = false) String targetType,
                                                @RequestParam(required = false) Long targetId,
                                                @RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(evaluationAppService.list(AuthContext.current(), period, targetType, targetId, limit));
    }

    @GetMapping("/{id}")
    public ApiResult<EvaluationView> detail(@PathVariable Long id) {
        return ApiResult.ok(evaluationAppService.detail(AuthContext.current(), id));
    }

    @PostMapping("/{id}/appeal")
    public ApiResult<EvaluationView> appeal(@PathVariable Long id,
                                            @RequestBody EvaluationAppealRequest request) {
        return ApiResult.ok(evaluationAppService.appeal(AuthContext.current(), id,
                request == null ? null : request.reason()));
    }
}
