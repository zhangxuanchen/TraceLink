package io.tracelink.api.web;

import io.tracelink.api.dto.TaskCancelRequest;
import io.tracelink.api.dto.TaskCheckinRequest;
import io.tracelink.api.dto.TaskCreateRequest;
import io.tracelink.api.dto.TaskDispatchRequest;
import io.tracelink.api.dto.TaskRescheduleRequest;
import io.tracelink.api.dto.TaskSubmitRequest;
import io.tracelink.api.dto.TaskVerifyRequest;
import io.tracelink.api.security.AuthContext;
import io.tracelink.app.task.CheckinCommand;
import io.tracelink.app.task.CheckinView;
import io.tracelink.app.task.DispatchCommand;
import io.tracelink.app.task.RescheduleCommand;
import io.tracelink.app.task.SubmitCommand;
import io.tracelink.app.task.TaskAppService;
import io.tracelink.app.task.TaskCounts;
import io.tracelink.app.task.TaskCreateCommand;
import io.tracelink.app.task.TaskDetailView;
import io.tracelink.app.task.TaskOperations;
import io.tracelink.app.task.TaskView;
import io.tracelink.app.task.VerifyCommand;
import io.tracelink.common.api.ApiResult;
import io.tracelink.common.api.PageResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskAppService taskAppService;

    public TaskController(TaskAppService taskAppService) {
        this.taskAppService = taskAppService;
    }

    @GetMapping
    public ApiResult<PageResult<TaskView>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Long primaryAssigneeId,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(defaultValue = "false") boolean blocked,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResult.ok(taskAppService.page(AuthContext.current(), keyword, status, type, deptId,
                ownerId, primaryAssigneeId, mine, overdue, blocked, pageNo, pageSize));
    }

    @GetMapping("/counts")
    public ApiResult<TaskCounts> counts() {
        return ApiResult.ok(taskAppService.counts(AuthContext.current()));
    }

    @GetMapping("/overdue")
    public ApiResult<List<TaskView>> overdue() {
        return ApiResult.ok(taskAppService.overdue(AuthContext.current()));
    }

    @GetMapping("/blocked")
    public ApiResult<List<TaskView>> blocked() {
        return ApiResult.ok(taskAppService.blocked(AuthContext.current()));
    }

    @GetMapping("/{id}")
    public ApiResult<TaskDetailView> detail(@PathVariable Long id) {
        return ApiResult.ok(taskAppService.detail(id, AuthContext.current()));
    }

    @GetMapping("/{id}/timeline")
    public ApiResult<List<CheckinView>> timeline(@PathVariable Long id) {
        return ApiResult.ok(taskAppService.timeline(id, AuthContext.current()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<TaskOperations.CreateResult> create(@Valid @RequestBody TaskCreateRequest request) {
        return ApiResult.ok(taskAppService.create(toCommand(request), AuthContext.current()));
    }

    @PostMapping("/{id}/dispatch")
    public ApiResult<TaskDetailView> dispatch(@PathVariable Long id,
                                              @Valid @RequestBody TaskDispatchRequest request) {
        return ApiResult.ok(taskAppService.dispatch(id, toCommand(request), AuthContext.current()));
    }

    @PostMapping("/{id}/checkin")
    public ApiResult<TaskOperations.CheckinResult> checkin(@PathVariable Long id,
                                                           @Valid @RequestBody TaskCheckinRequest request) {
        return ApiResult.ok(taskAppService.checkin(id, toCommand(request), AuthContext.current()));
    }

    @PostMapping("/{id}/submit")
    public ApiResult<TaskDetailView> submit(@PathVariable Long id,
                                            @Valid @RequestBody TaskSubmitRequest request) {
        return ApiResult.ok(taskAppService.submit(id, toCommand(request), AuthContext.current()));
    }

    @PostMapping("/{id}/verify")
    public ApiResult<TaskOperations.VerifyResult> verify(@PathVariable Long id,
                                                         @Valid @RequestBody TaskVerifyRequest request) {
        return ApiResult.ok(taskAppService.verify(id, toCommand(request), AuthContext.current()));
    }

    @PostMapping("/{id}/reschedule")
    public ApiResult<TaskView> reschedule(@PathVariable Long id,
                                          @Valid @RequestBody TaskRescheduleRequest request) {
        return ApiResult.ok(taskAppService.reschedule(id, toCommand(request), AuthContext.current()));
    }

    @PostMapping("/{id}/urge")
    public ApiResult<TaskOperations.UrgeResult> urge(@PathVariable Long id) {
        return ApiResult.ok(taskAppService.urge(id, AuthContext.current()));
    }

    @PostMapping("/{id}/cancel")
    public ApiResult<TaskView> cancel(@PathVariable Long id,
                                      @Valid @RequestBody TaskCancelRequest request) {
        return ApiResult.ok(taskAppService.cancel(id, request.reason(), AuthContext.current()));
    }

    @PostMapping("/{id}/issues/{issueId}/close")
    public ApiResult<TaskDetailView> closeIssue(@PathVariable Long id, @PathVariable Long issueId) {
        return ApiResult.ok(taskAppService.closeIssue(id, issueId, AuthContext.current()));
    }

    @PostMapping("/{id}/actions/{actionId}/done")
    public ApiResult<TaskDetailView> doneAction(@PathVariable Long id, @PathVariable Long actionId) {
        return ApiResult.ok(taskAppService.doneAction(id, actionId, AuthContext.current()));
    }

    private TaskCreateCommand toCommand(TaskCreateRequest request) {
        List<TaskCreateCommand.TaskActionInput> actions = request.actionList() == null ? List.of()
                : request.actionList().stream()
                .filter(a -> a != null)
                .map(a -> new TaskCreateCommand.TaskActionInput(a.content(), a.ownerId(), a.dueDate()))
                .toList();
        return new TaskCreateCommand(request.title(), request.description(), request.type(),
                request.sourceType(), request.deptId(), request.ownerId(), request.primaryAssigneeId(),
                request.coAssigneeIds(), request.assigneeText(), request.priority(), request.planStart(),
                request.planEnd(), request.acceptCriteria(), request.issueList(), actions, request.tags());
    }

    private DispatchCommand toCommand(TaskDispatchRequest request) {
        return new DispatchCommand(request.primaryAssigneeId(), request.coAssigneeIds(),
                request.planEnd(), request.priority(), request.note());
    }

    private CheckinCommand toCommand(TaskCheckinRequest request) {
        return new CheckinCommand(request.progress(), request.content(), request.blockerText(),
                request.nextStep(), request.resultType(), request.checkDate(), request.resolvedBlocker());
    }

    private SubmitCommand toCommand(TaskSubmitRequest request) {
        return new SubmitCommand(request.resultSummary(), request.criteriaChecks());
    }

    private VerifyCommand toCommand(TaskVerifyRequest request) {
        return new VerifyCommand(request.decision(), request.resultSummary(), request.rejectReason(),
                request.criteriaChecks());
    }

    private RescheduleCommand toCommand(TaskRescheduleRequest request) {
        return new RescheduleCommand(request.newPlanEnd(), request.oldPlanEnd(), request.reason(),
                request.reasonText(), request.rootCause(), request.measure());
    }
}
