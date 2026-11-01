package io.tracelink.api.web;

import io.tracelink.api.dto.PositionRequest;
import io.tracelink.app.org.PositionAppService;
import io.tracelink.app.org.PositionCommand;
import io.tracelink.common.api.ApiResult;
import io.tracelink.domain.org.model.Position;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/positions")
public class PositionController {

    private final PositionAppService positionAppService;

    public PositionController(PositionAppService positionAppService) {
        this.positionAppService = positionAppService;
    }

    @GetMapping
    public ApiResult<List<Position>> list(@RequestParam(required = false) Long deptId) {
        return ApiResult.ok(positionAppService.list(deptId));
    }

    @PostMapping
    public ApiResult<Position> create(@Valid @RequestBody PositionRequest request) {
        return ApiResult.ok(positionAppService.create(toCommand(request)));
    }

    @PutMapping("/{id}")
    public ApiResult<Position> update(@PathVariable Long id, @Valid @RequestBody PositionRequest request) {
        return ApiResult.ok(positionAppService.update(id, toCommand(request)));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        positionAppService.delete(id);
        return ApiResult.ok();
    }

    private PositionCommand toCommand(PositionRequest request) {
        return new PositionCommand(request.deptId(), request.code(), request.name(),
                request.jobLevel(), request.isManager(), request.sortNo(), request.status());
    }
}
