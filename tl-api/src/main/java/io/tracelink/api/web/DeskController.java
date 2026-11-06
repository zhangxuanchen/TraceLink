package io.tracelink.api.web;

import io.tracelink.api.security.AuthContext;
import io.tracelink.app.desk.DeskAppService;
import io.tracelink.app.desk.DeskView;
import io.tracelink.common.api.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 我的工作台：今日待办、待我验收、退回待改、我的缺口 */
@RestController
@RequestMapping("/api/v1/desk")
public class DeskController {

    private final DeskAppService deskAppService;

    public DeskController(DeskAppService deskAppService) {
        this.deskAppService = deskAppService;
    }

    @GetMapping
    public ApiResult<DeskView> desk() {
        return ApiResult.ok(deskAppService.desk(AuthContext.current()));
    }
}
