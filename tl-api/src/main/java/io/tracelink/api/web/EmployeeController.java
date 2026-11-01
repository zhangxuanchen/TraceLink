package io.tracelink.api.web;

import io.tracelink.api.dto.EmployeeRequest;
import io.tracelink.app.org.EmployeeAppService;
import io.tracelink.app.org.EmployeeCommand;
import io.tracelink.app.org.EmployeeView;
import io.tracelink.common.api.ApiResult;
import io.tracelink.common.api.PageResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    private final EmployeeAppService employeeAppService;

    public EmployeeController(EmployeeAppService employeeAppService) {
        this.employeeAppService = employeeAppService;
    }

    @GetMapping
    public ApiResult<PageResult<EmployeeView>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResult.ok(employeeAppService.page(keyword, deptId, status, pageNo, pageSize));
    }

    @GetMapping("/options")
    public ApiResult<List<EmployeeView>> options() {
        return ApiResult.ok(employeeAppService.options());
    }

    /** ORG-3：姓名解析，供导入预览与智能会话把自由文本里的人名落成员工 */
    @GetMapping("/resolve")
    public ApiResult<List<EmployeeView>> resolve(@RequestParam String name) {
        return ApiResult.ok(employeeAppService.resolve(name));
    }

    @GetMapping("/{id}")
    public ApiResult<EmployeeView> detail(@PathVariable Long id) {
        return ApiResult.ok(employeeAppService.detail(id));
    }

    @PostMapping
    public ApiResult<EmployeeView> create(@Valid @RequestBody EmployeeRequest request) {
        return ApiResult.ok(employeeAppService.create(toCommand(request)));
    }

    @PutMapping("/{id}")
    public ApiResult<EmployeeView> update(@PathVariable Long id, @Valid @RequestBody EmployeeRequest request) {
        return ApiResult.ok(employeeAppService.update(id, toCommand(request)));
    }

    private EmployeeCommand toCommand(EmployeeRequest request) {
        return new EmployeeCommand(request.empNo(), request.name(), request.deptId(),
                request.positionId(), request.managerId(), request.phone(), request.email(),
                request.status(), request.joinDate());
    }
}
