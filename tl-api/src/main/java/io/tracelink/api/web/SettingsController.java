package io.tracelink.api.web;

import io.tracelink.api.dto.AccountRolesRequest;
import io.tracelink.api.dto.ConfigUpdateRequest;
import io.tracelink.api.dto.GateRuleUpdateRequest;
import io.tracelink.api.security.AuthContext;
import io.tracelink.app.settings.AccountView;
import io.tracelink.app.settings.ConfigView;
import io.tracelink.app.settings.GateSettingsView;
import io.tracelink.app.settings.RoleView;
import io.tracelink.app.settings.SettingsAppService;
import io.tracelink.common.api.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 模板与参数、九条件权重、角色与数据范围：DESIGN 9.2 的三个设置页 */
@RestController
@RequestMapping("/api/v1")
public class SettingsController {

    private final SettingsAppService settingsAppService;

    public SettingsController(SettingsAppService settingsAppService) {
        this.settingsAppService = settingsAppService;
    }

    @GetMapping("/configs")
    public ApiResult<List<ConfigView>> configs() {
        return ApiResult.ok(settingsAppService.configs(AuthContext.current()));
    }

    @PutMapping("/configs/{key}")
    public ApiResult<ConfigView> updateConfig(@PathVariable String key,
                                              @RequestBody ConfigUpdateRequest request) {
        return ApiResult.ok(settingsAppService.updateConfig(AuthContext.current(), key, request.value()));
    }

    @GetMapping("/gate-rules")
    public ApiResult<GateSettingsView> gateRules() {
        return ApiResult.ok(settingsAppService.gateRules(AuthContext.current()));
    }

    @PutMapping("/gate-rules/{id}")
    public ApiResult<GateSettingsView> updateGateRule(@PathVariable Long id,
                                                      @RequestBody GateRuleUpdateRequest request) {
        return ApiResult.ok(settingsAppService.updateGateRule(AuthContext.current(), id,
                request.weight(), request.enabled()));
    }

    @GetMapping("/roles")
    public ApiResult<List<RoleView>> roles() {
        return ApiResult.ok(settingsAppService.roles(AuthContext.current()));
    }

    @GetMapping("/accounts")
    public ApiResult<List<AccountView>> accounts() {
        return ApiResult.ok(settingsAppService.accounts(AuthContext.current()));
    }

    @PutMapping("/accounts/{employeeId}/roles")
    public ApiResult<List<AccountView>> updateAccountRoles(@PathVariable Long employeeId,
                                                           @RequestBody AccountRolesRequest request) {
        return ApiResult.ok(settingsAppService.updateAccountRoles(AuthContext.current(), employeeId,
                request.roles()));
    }
}
