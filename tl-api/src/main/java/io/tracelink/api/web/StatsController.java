package io.tracelink.api.web;

import io.tracelink.api.security.AuthContext;
import io.tracelink.app.stats.KpiFilter;
import io.tracelink.app.stats.StatsAppService;
import io.tracelink.app.stats.StatsOverviewView;
import io.tracelink.app.stats.StatsPersonView;
import io.tracelink.app.stats.StatsProcessView;
import io.tracelink.app.stats.StatsRatesView;
import io.tracelink.app.stats.StatsTrendView;
import io.tracelink.common.api.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
                                                 @RequestParam(required = false) String deptId,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String type,
                                                 @RequestParam(required = false) Boolean overdue,
                                                 @RequestParam(required = false) Boolean blocked,
                                                 @RequestParam(required = false) Boolean mine) {
        List<Long> deptIds = splitDeptIds(deptId);
        KpiFilter filter = new KpiFilter(keyword, splitCsv(status), splitCsv(type), deptIds,
                Boolean.TRUE.equals(overdue), Boolean.TRUE.equals(blocked), Boolean.TRUE.equals(mine));
        // 只选一个部门时仍按部门收窄快照；多选时快照走角色范围，部门在指标谓词里取并集
        Long singleDept = deptIds != null && deptIds.size() == 1 ? deptIds.get(0) : null;
        return ApiResult.ok(statsAppService.overview(AuthContext.current(), from, to, singleDept, filter));
    }

    @GetMapping("/rates")
    public ApiResult<StatsRatesView> rates(@RequestParam(required = false) LocalDate from,
                                           @RequestParam(required = false) LocalDate to,
                                           @RequestParam(required = false) String deptId) {
        List<Long> deptIds = splitDeptIds(deptId);
        Long singleDept = deptIds != null && deptIds.size() == 1 ? deptIds.get(0) : null;
        return ApiResult.ok(statsAppService.rates(AuthContext.current(), from, to, singleDept));
    }

    private static List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        List<String> items = new ArrayList<>();
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed.toUpperCase());
            }
        }
        return items.isEmpty() ? null : items;
    }

    private static List<Long> splitDeptIds(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        List<Long> items = new ArrayList<>();
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                items.add(Long.valueOf(trimmed));
            }
        }
        return items.isEmpty() ? null : items;
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
