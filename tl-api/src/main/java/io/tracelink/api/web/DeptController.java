package io.tracelink.api.web;

import io.tracelink.api.dto.DeptRequest;
import io.tracelink.app.org.DeptAppService;
import io.tracelink.app.org.DeptCommand;
import io.tracelink.common.api.ApiResult;
import io.tracelink.domain.org.model.Dept;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/depts")
public class DeptController {

    private final DeptAppService deptAppService;

    public DeptController(DeptAppService deptAppService) {
        this.deptAppService = deptAppService;
    }

    @GetMapping("/tree")
    public ApiResult<List<Dept>> tree() {
        return ApiResult.ok(deptAppService.tree());
    }

    @PostMapping
    public ApiResult<Dept> create(@Valid @RequestBody DeptRequest request) {
        return ApiResult.ok(deptAppService.create(toCommand(request)));
    }

    @PutMapping("/{id}")
    public ApiResult<Dept> update(@PathVariable Long id, @Valid @RequestBody DeptRequest request) {
        return ApiResult.ok(deptAppService.update(id, toCommand(request)));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        deptAppService.delete(id);
        return ApiResult.ok();
    }

    private DeptCommand toCommand(DeptRequest request) {
        return new DeptCommand(request.code(), request.name(), request.parentId(),
                request.leaderEmployeeId(), request.headcount(), request.sortNo(), request.status());
    }
}
