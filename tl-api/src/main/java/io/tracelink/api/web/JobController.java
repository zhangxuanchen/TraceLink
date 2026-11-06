package io.tracelink.api.web;

import io.tracelink.api.security.AuthContext;
import io.tracelink.app.job.JobAppService;
import io.tracelink.app.job.JobView;
import io.tracelink.common.api.ApiResult;
import io.tracelink.common.api.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 异步任务台账：导入、导出、评估、快照的排队与结果查询 */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobAppService jobAppService;

    public JobController(JobAppService jobAppService) {
        this.jobAppService = jobAppService;
    }

    @GetMapping
    public ApiResult<PageResult<JobView>> page(@RequestParam(required = false) String jobType,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(defaultValue = "1") int pageNo,
                                               @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResult.ok(jobAppService.page(AuthContext.current(), jobType, status, pageNo, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResult<JobView> detail(@PathVariable Long id) {
        return ApiResult.ok(jobAppService.get(AuthContext.current(), id));
    }
}
