package io.tracelink.api.web;

import io.tracelink.api.security.AuthContext;
import io.tracelink.app.ai.SuggestionAcceptView;
import io.tracelink.app.ai.SuggestionAppService;
import io.tracelink.app.ai.SuggestionRunView;
import io.tracelink.app.ai.SuggestionView;
import io.tracelink.common.api.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** AI-1/AI-2：五类建议的生成、列表、详情与采纳（采纳由应用服务执行确定性动作） */
@RestController
@RequestMapping("/api/v1/ai/suggestions")
public class SuggestionController {

    private final SuggestionAppService suggestionAppService;

    public SuggestionController(SuggestionAppService suggestionAppService) {
        this.suggestionAppService = suggestionAppService;
    }

    @PostMapping("/run")
    public ApiResult<SuggestionRunView> run(@RequestParam(required = false) String type) {
        return ApiResult.ok(suggestionAppService.run(AuthContext.current(), type));
    }

    @GetMapping
    public ApiResult<List<SuggestionView>> list(@RequestParam(required = false) String type,
                                                @RequestParam(required = false) String targetType,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(suggestionAppService.list(AuthContext.current(), type, targetType, status, limit));
    }

    @GetMapping("/{id}")
    public ApiResult<SuggestionView> detail(@PathVariable Long id) {
        return ApiResult.ok(suggestionAppService.detail(AuthContext.current(), id));
    }

    @PostMapping("/{id}/accept")
    public ApiResult<SuggestionAcceptView> accept(@PathVariable Long id) {
        return ApiResult.ok(suggestionAppService.accept(AuthContext.current(), id));
    }
}
