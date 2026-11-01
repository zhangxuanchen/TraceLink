package io.tracelink.api.web;

import io.tracelink.api.security.AuthContext;
import io.tracelink.app.stats.StatsAppService;
import io.tracelink.app.stats.StatsOverviewView;
import io.tracelink.app.stats.StatsPersonView;
import io.tracelink.app.stats.StatsProcessView;
import io.tracelink.app.stats.StatsTrendView;
import io.tracelink.common.api.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** STA-1 到 STA-4：驾驶舱、趋势、工序达成、人员分析 */
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final StatsAppService statsAppService;

    public StatsController(StatsAppService statsAppService) {
        this.statsAppService = statsAppService;
    }

    @GetMapping("/overview")
    public ApiResult<StatsOverviewView> overview(@RequestParam(required = false) LocalDate from,
                                                 @RequestParam(required = false) LocalDate to,
                                                 @RequestParam(required = false) Long deptId) {
        return ApiResult.ok(statsAppService.overview(AuthContext.current(), from, to, deptId));
    }

    @GetMapping("/trend")
    public ApiResult<StatsTrendView> trend(@RequestParam(required = false) LocalDate from,
                                           @RequestParam(required = false) LocalDate to,
                                           @RequestParam(required = false) Long deptId) {
        return ApiResult.ok(statsAppService.trend(AuthContext.current(), from, to, deptId));
    }

    @GetMapping("/process")
    public ApiResult<StatsProcessView> process(@RequestParam(required = false) LocalDate from,
                                                @RequestParam(required = false) LocalDate to) {
        return ApiResult.ok(statsAppService.process(AuthContext.current(), from, to));
    }

    @GetMapping("/person")
    public ApiResult<StatsPersonView> person(@RequestParam(required = false) LocalDate from,
                                             @RequestParam(required = false) LocalDate to,
                                             @RequestParam(required = false) Long deptId,
                                             @RequestParam(required = false) Long employeeId) {
        return ApiResult.ok(statsAppService.person(AuthContext.current(), from, to, deptId, employeeId));
    }
}
