package io.tracelink.api.web;

import io.tracelink.api.dto.AiConfigUpdateRequest;
import io.tracelink.api.security.AuthContext;
import io.tracelink.app.settings.AiConfigService;
import io.tracelink.common.api.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 模型配置页：读当前配置、保存火山方舟 AK 与模型 ID、用当前配置打一次连通性自检 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiConfigController {

    private final AiConfigService aiConfigService;

    public AiConfigController(AiConfigService aiConfigService) {
        this.aiConfigService = aiConfigService;
    }

    @GetMapping("/config")
    public ApiResult<AiConfigService.AiConfigView> config() {
        return ApiResult.ok(aiConfigService.view(AuthContext.current()));
    }

    @PutMapping("/config")
    public ApiResult<AiConfigService.AiConfigView> updateConfig(@RequestBody AiConfigUpdateRequest request) {
        return ApiResult.ok(aiConfigService.update(AuthContext.current(),
                request.baseUrl(), request.chatModel(), request.apiKey()));
    }

    @PostMapping("/config/test")
    public ApiResult<AiConfigService.AiTestResult> test() {
        return ApiResult.ok(aiConfigService.test(AuthContext.current()));
    }
}
