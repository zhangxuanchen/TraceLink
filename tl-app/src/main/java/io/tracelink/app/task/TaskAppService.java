package io.tracelink.app.task;

import io.tracelink.common.api.PageResult;
import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.audit.model.AuditLog;
import io.tracelink.domain.audit.repository.AuditLogRepository;
import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.org.model.Dept;
import io.tracelink.domain.org.model.Employee;
import io.tracelink.domain.org.repository.DeptRepository;
import io.tracelink.domain.org.repository.EmployeeRepository;
import io.tracelink.domain.org.rules.DataScopeResolver;
import io.tracelink.domain.org.rules.DeptTree;
import io.tracelink.domain.task.model.ActionStatus;
import io.tracelink.domain.task.model.AssigneeRoleType;
import io.tracelink.domain.task.model.CheckinResultType;
import io.tracelink.domain.task.model.ClosureLevel;
import io.tracelink.domain.task.model.IssueStatus;
import io.tracelink.domain.task.model.RescheduleReason;
import io.tracelink.domain.task.model.Task;
import io.tracelink.domain.task.model.TaskAction;
import io.tracelink.domain.task.model.TaskAssignee;
import io.tracelink.domain.task.model.TaskCheckin;
import io.tracelink.domain.task.model.TaskIssue;
import io.tracelink.domain.task.model.TaskQuery;
import io.tracelink.domain.task.model.TaskScope;
import io.tracelink.domain.task.model.TaskSourceType;
import io.tracelink.domain.task.model.TaskStatus;
import io.tracelink.domain.task.model.TaskType;
import io.tracelink.domain.task.repository.SysConfigRepository;
import io.tracelink.domain.task.repository.TaskActionRepository;
import io.tracelink.domain.task.repository.TaskAssigneeRepository;
import io.tracelink.domain.task.repository.TaskCheckinRepository;
import io.tracelink.domain.task.repository.TaskIssueRepository;
import io.tracelink.domain.task.repository.TaskRepository;
import io.tracelink.domain.task.rules.OverdueLevel;
import io.tracelink.domain.task.rules.TaskStateMachine;
import io.tracelink.domain.task.rules.TitleSimilarity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 工单与追踪的应用服务。事务边界按 DESIGN 6.5 划在每个写操作上；
 * 状态与规则判定全部下沉到 TaskStateMachine，这里只负责数据、权限与审计。
 */
@Service
public class TaskAppService {

    private static final String BIZ_TASK = "TASK";
    private static final String ROLE_ADMIN = "SYS_ADMIN";
    private static final String ROLE_MANAGER = "MANAGER";
    private static final String ROLE_OWNER = "OWNER";
    private static final int MAX_PAGE_SIZE = 200;

    private final TaskRepository taskRepository;
    private final TaskCheckinRepository checkinRepository;
    private final TaskIssueRepository issueRepository;
    private final TaskActionRepository actionRepository;
    private final TaskAssigneeRepository assigneeRepository;
    private final DeptRepository deptRepository;
    private final EmployeeRepository employeeRepository;
    private final SysConfigRepository sysConfigRepository;
    private final AuditLogRepository auditLogRepository;

    public TaskAppService(TaskRepository taskRepository,
                          TaskCheckinRepository checkinRepository,
                          TaskIssueRepository issueRepository,
                          TaskActionRepository actionRepository,
                          TaskAssigneeRepository assigneeRepository,
                          DeptRepository deptRepository,
                          EmployeeRepository employeeRepository,
                          SysConfigRepository sysConfigRepository,
                          AuditLogRepository auditLogRepository) {
        this.taskRepository = taskRepository;
        this.checkinRepository = checkinRepository;
        this.issueRepository = issueRepository;
        this.actionRepository = actionRepository;
        this.assigneeRepository = assigneeRepository;
        this.deptRepository = deptRepository;
        this.employeeRepository = employeeRepository;
        this.sysConfigRepository = sysConfigRepository;
        this.auditLogRepository = auditLogRepository;
    }

    // ---------------------------------------------------------------- 查询

    public PageResult<TaskView> page(AuthUser actor, String keyword, String status, String type, Long deptId,
                                     Long ownerId, Long primaryAssigneeId, boolean mine, boolean overdueOnly,
                                     boolean blockedOnly, int pageNo, int pageSize) {
        TaskScope scope = scopeOf(actor);
        if (deptId != null) {
            assertDeptInScope(deptId, scope);
        }
        TaskQuery query = new TaskQuery(keyword, parseStatus(status), parseType(type), deptId, ownerId,
                primaryAssigneeId, mine, overdueOnly, blockedOnly, LocalDateTime.now(), scope,
                Math.max(1, pageNo), Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE));
        PageResult<Task> page = taskRepository.page(query);
        Map<Long, String> deptNames = deptNameMap();
        Map<Long, String> employeeNames = employeeNameMap();
        List<TaskView> views = page.records().stream()
                .map(t -> toView(t, actor, deptNames, employeeNames, null))
                .toList();
        return new PageResult<>(page.total(), page.pageNo(), page.pageSize(), views);
    }

    public TaskDetailView detail(Long id, AuthUser actor) {
        Task task = visibleTask(id, actor);
        List<TaskIssue> issues = issueRepository.listByTask(id);
        List<TaskAction> actions = actionRepository.listByTask(id);
        List<TaskAssignee> assignees = assigneeRepository.listByTask(id);
        Map<Long, String> deptNames = deptNameMap();
        Map<Long, String> employeeNames = employeeNameMap();
        TaskView view = toView(task, actor, deptNames, employeeNames, assignees);

        List<TaskDetailView.IssueView> issueViews = issues.stream()
                .map(i -> new TaskDetailView.IssueView(i.id, i.seq, i.content, i.category, i.severity,
                        i.status.name(), i.status.label(), i.closedAt))
                .toList();
        LocalDate today = LocalDate.now();
        List<TaskDetailView.ActionView> actionViews = actions.stream()
                .map(a -> new TaskDetailView.ActionView(a.id, a.issueId, a.seq, a.content, a.ownerId,
                        employeeNames.get(a.ownerId), a.dueDate, a.status.name(), a.status.label(),
                        a.status == ActionStatus.OPEN && a.dueDate != null && a.dueDate.isBefore(today)))
                .toList();
        List<TaskDetailView.AssigneeView> assigneeViews = assignees.stream()
                .map(a -> new TaskDetailView.AssigneeView(a.employeeId, employeeNames.get(a.employeeId),
                        a.roleType.name(), a.roleType.label()))
                .toList();
        return new TaskDetailView(view, task.description, TaskJson.strings(task.acceptCriteria),
                task.resultSummary, issueViews, actionViews, assigneeViews, submitGaps(task, issues));
    }

    public List<CheckinView> timeline(Long id, AuthUser actor) {
        visibleTask(id, actor);
        Map<Long, String> employeeNames = employeeNameMap();
        LocalDate today = LocalDate.now();
        return checkinRepository.listByTask(id).stream()
                .map(c -> new CheckinView(c.id, c.employeeId, employeeNames.get(c.employeeId), c.checkDate,
                        c.progress, c.content, c.blockerText, c.nextStep,
                        c.resultType.name(), c.resultType.label(),
                        c.closureLevel == null ? null : c.closureLevel.name(),
                        c.closureLevel == null ? null : c.closureLevel.label(),
                        c.checkDate != null && c.checkDate.isBefore(today), c.createdAt))
                .toList();
    }

    public List<TaskView> overdue(AuthUser actor) {
        return listed(actor, true, false);
    }

    public List<TaskView> blocked(AuthUser actor) {
        return listed(actor, false, true);
    }

    public TaskCounts counts(AuthUser actor) {
        TaskScope scope = scopeOf(actor);
        return new TaskCounts(
                count(scope, TaskStatus.PENDING, false, false),
                count(scope, TaskStatus.IN_PROGRESS, false, false),
                count(scope, TaskStatus.BLOCKED, false, false),
                count(scope, TaskStatus.SUBMITTED, false, false),
                count(scope, TaskStatus.DONE, false, false),
                count(scope, null, false, true),
                count(scope, null, true, false));
    }

    private List<TaskView> listed(AuthUser actor, boolean overdueOnly, boolean blockedOnly) {
        TaskScope scope = scopeOf(actor);
        TaskQuery query = new TaskQuery(null, null, null, null, null, null, false,
                overdueOnly, blockedOnly, LocalDateTime.now(), scope, 1, MAX_PAGE_SIZE);
        Map<Long, String> deptNames = deptNameMap();
        Map<Long, String> employeeNames = employeeNameMap();
        return taskRepository.page(query).records().stream()
                .map(t -> toView(t, actor, deptNames, employeeNames, null))
                .toList();
    }

    // ---------------------------------------------------------------- 写操作

    @Transactional
    public TaskOperations.CreateResult create(TaskCreateCommand cmd, AuthUser actor) {
        requireAnyRole(actor, ROLE_ADMIN, ROLE_MANAGER, ROLE_OWNER);
        String title = requireTitle(cmd.title());
        TaskType type = parseType(requireText(cmd.type(), "请选择工单类型"));
        TaskScope scope = scopeOf(actor);
        if (cmd.deptId() == null) {
            throw BizException.invalid("请选择承办部门");
        }
        assertDeptInScope(cmd.deptId(), scope);
        Dept dept = deptRepository.findById(cmd.deptId());
        if (dept == null) {
            throw BizException.notFound("承办部门不存在，请刷新后重试");
        }
        Employee owner = requireActiveEmployee(cmd.ownerId(), "请选择负责人");
        Employee assignee = requireActiveEmployee(cmd.primaryAssigneeId(), "请选择主责执行人");
        TaskStateMachine.assertAssigneeDistinct(owner.getId(), assignee.getId());
        requireOwnerAboveDept(owner, dept);
        requireAssigneeUnderDept(assignee, dept);
        TaskStateMachine.assertPlanEnd(cmd.planEnd(), LocalDateTime.now());

        int priority = cmd.priority() == null ? 2 : cmd.priority();
        if (priority < 0 || priority > 3) {
            throw BizException.invalid("优先级只能是 0 到 3");
        }
        List<String> criteria = cmd.acceptCriteria() == null ? List.of() : cmd.acceptCriteria().stream()
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (criteria.size() > 20) {
            throw BizException.invalid("验收条件不超过 20 条");
        }
        for (String c : criteria) {
            if (c.length() > 200) {
                throw BizException.invalid("单条验收条件不超过 200 字");
            }
        }

        Task duplicate = findSameTitleOpenTask(dept.getId(), title);
        if (duplicate != null) {
            throw BizException.duplicate("同部门已有同标题在建工单 " + duplicate.taskNo + "，请并入或换个标题");
        }

        Task task = new Task();
        task.title = title;
        task.description = cmd.description();
        task.type = type;
        task.sourceType = parseSourceType(cmd.sourceType());
        task.deptId = dept.getId();
        task.ownerId = owner.getId();
        task.primaryAssigneeId = assignee.getId();
        task.assigneeText = cmd.assigneeText();
        task.priority = priority;
        task.status = TaskStatus.PENDING;
        task.planStart = cmd.planStart();
        task.planEnd = cmd.planEnd();
        task.progress = 0;
        task.acceptCriteria = TaskJson.array(criteria);
        task.delayCount = 0;
        task.rejectCount = 0;
        task.urgeCount = 0;
        task.tags = cmd.tags();
        task.createdBy = actor.employeeId();
        task.createdAt = LocalDateTime.now();
        task.taskNo = taskRepository.nextTaskNo(LocalDate.now());
        task.recurrenceOf = findRecurrence(dept.getId(), title);
        taskRepository.insert(task);

        List<String> issues = cmd.issueList() == null ? List.of() : cmd.issueList().stream()
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).toList();
        for (int i = 0; i < issues.size(); i++) {
            if (issues.get(i).length() > 500) {
                throw BizException.invalid("单条问题描述不超过 500 字");
            }
            TaskIssue issue = new TaskIssue();
            issue.taskId = task.id;
            issue.seq = i + 1;
            issue.content = issues.get(i);
            issue.severity = 2;
            issue.status = IssueStatus.OPEN;
            issueRepository.insert(issue);
        }
        List<TaskCreateCommand.TaskActionInput> actionInputs = cmd.actionList() == null ? List.of() : cmd.actionList();
        for (int i = 0; i < actionInputs.size(); i++) {
            TaskCreateCommand.TaskActionInput input = actionInputs.get(i);
            if (input == null || input.content() == null || input.content().isBlank()) {
                continue;
            }
            TaskAction action = new TaskAction();
            action.taskId = task.id;
            action.seq = i + 1;
            action.content = input.content().trim();
            action.ownerId = input.ownerId() == null ? assignee.getId() : input.ownerId();
            action.dueDate = input.dueDate();
            action.status = ActionStatus.OPEN;
            actionRepository.insert(action);
        }
        insertCoAssignees(task, cmd.coAssigneeIds(), assignee.getId());

        audit(actor, "CREATE", task.id, null, Map.of(
                "taskNo", task.taskNo,
                "status", task.status.name(),
                "title", task.title,
                "ownerId", String.valueOf(task.ownerId),
                "primaryAssigneeId", String.valueOf(task.primaryAssigneeId),
                "planEnd", String.valueOf(task.planEnd)));
        return new TaskOperations.CreateResult(task.id, task.taskNo, task.status.name(), task.createdAt);
    }

    @Transactional
    public TaskDetailView dispatch(Long id, DispatchCommand cmd, AuthUser actor) {
        Task task = visibleTask(id, actor);
        boolean ownerLike = Objects.equals(actor.employeeId(), task.ownerId) || isManagerLike(actor);
        if (!ownerLike) {
            throw new BizException(ErrorCode.NO_PERMISSION, "只有负责人或管理者可以派工");
        }
        TaskStateMachine.assertDispatchAllowed(task.status);
        if (cmd.primaryAssigneeId() != null && !Objects.equals(cmd.primaryAssigneeId(), task.primaryAssigneeId)) {
            Employee assignee = requireActiveEmployee(cmd.primaryAssigneeId(), "请选择主责执行人");
            TaskStateMachine.assertAssigneeDistinct(task.ownerId, assignee.getId());
            Dept dept = deptRepository.findById(task.deptId);
            requireAssigneeUnderDept(assignee, dept);
            task.primaryAssigneeId = assignee.getId();
        }
        if (cmd.planEnd() != null) {
            TaskStateMachine.assertPlanEnd(cmd.planEnd(), LocalDateTime.now());
            task.planEnd = cmd.planEnd();
        }
        if (cmd.priority() != null) {
            if (cmd.priority() < 0 || cmd.priority() > 3) {
                throw BizException.invalid("优先级只能是 0 到 3");
            }
            task.priority = cmd.priority();
        }
        if (task.status == TaskStatus.DRAFT && task.primaryAssigneeId != null && task.planEnd != null) {
            task.status = TaskStatus.PENDING;
        }
        taskRepository.update(task);
        insertCoAssignees(task, cmd.coAssigneeIds(), task.primaryAssigneeId);
        audit(actor, "UPDATE", task.id, null, Map.of(
                "action", "DISPATCH",
                "status", task.status.name(),
                "primaryAssigneeId", String.valueOf(task.primaryAssigneeId),
                "planEnd", String.valueOf(task.planEnd),
                "note", cmd.note() == null ? "" : cmd.note()));
        return detail(id, actor);
    }

    @Transactional
    public TaskOperations.CheckinResult checkin(Long id, CheckinCommand cmd, AuthUser actor) {
        Task task = visibleTask(id, actor);
        if (!canWriteCheckin(task, actor)) {
            throw new BizException(ErrorCode.NO_PERMISSION, "只有主责执行人、协办人或负责人可以写追踪记录");
        }
        TaskStateMachine.assertCheckinAllowed(task.status);
        int progress = cmd.progress() == null ? task.progressValue() : cmd.progress();
        if (progress < 0 || progress > 100) {
            throw BizException.invalid("进度只能是 0 到 100");
        }
        int recorded = Math.max(task.progressValue(), checkinRepository.maxProgress(id));
        if (progress < recorded) {
            throw new BizException(ErrorCode.PROGRESS_REGRESSION,
                    "进度不能回退，当前已记录 " + recorded + "%，新记录不能低于它");
        }
        String content = requireText(cmd.content(), "请写一段本次推进的说明");
        if (content.length() > 2000) {
            throw BizException.invalid("说明不超过 2000 字");
        }
        CheckinResultType resultType = parseResultType(cmd.resultType());
        if (resultType == CheckinResultType.RESULT && content.length() < 20) {
            throw new BizException(ErrorCode.RESULT_WITHOUT_EVIDENCE,
                    "结果类记录要写清做了什么、结果如何，至少 20 字（附件证据在 M2 开放）");
        }
        LocalDate checkDate = cmd.checkDate() == null ? LocalDate.now() : cmd.checkDate();
        if (checkDate.isAfter(LocalDate.now())) {
            throw BizException.invalid("业务日期不能晚于今天");
        }
        TaskStatus before = task.status;
        TaskStatus next = task.status;
        if (cmd.blockerText() != null && !cmd.blockerText().isBlank()) {
            next = TaskStatus.BLOCKED;
        } else if (next == TaskStatus.PENDING) {
            next = TaskStatus.IN_PROGRESS;
        } else if (next == TaskStatus.BLOCKED && cmd.resolvedBlocker()) {
            next = TaskStatus.IN_PROGRESS;
        }

        TaskCheckin checkin = new TaskCheckin();
        checkin.taskId = id;
        checkin.employeeId = actor.employeeId();
        checkin.checkDate = checkDate;
        checkin.progress = progress;
        checkin.content = content;
        checkin.blockerText = cmd.blockerText();
        checkin.nextStep = cmd.nextStep();
        checkin.resultType = resultType;
        checkin.closureLevel = ClosureLevel.WEAK;
        checkinRepository.insert(checkin);

        task.progress = progress;
        task.status = next;
        taskRepository.update(task);
        audit(actor, "STATUS", id, Map.of("status", before.name()),
                Map.of("status", next.name(), "progress", String.valueOf(progress), "checkinId", String.valueOf(checkin.id)));
        return new TaskOperations.CheckinResult(checkin.id, next.name(), progress, checkin.closureLevel.name());
    }

    @Transactional
    public TaskDetailView submit(Long id, SubmitCommand cmd, AuthUser actor) {
        Task task = visibleTask(id, actor);
        if (!Objects.equals(actor.employeeId(), task.primaryAssigneeId) && !hasRole(actor, ROLE_ADMIN)) {
            throw new BizException(ErrorCode.NO_PERMISSION, "只有主责执行人可以提交验收");
        }
        TaskStateMachine.assertSubmitAllowed(task.status);
        TaskStateMachine.assertHasResultRecord(checkinRepository.countByResultType(id, CheckinResultType.RESULT));
        String summary = requireText(cmd.resultSummary(), "提交验收要填写结果说明");
        if (summary.length() > 2000) {
            throw BizException.invalid("结果说明不超过 2000 字");
        }
        List<String> criteria = TaskJson.strings(task.acceptCriteria);
        if (!criteria.isEmpty()) {
            if (cmd.criteriaChecks() == null || cmd.criteriaChecks().size() != criteria.size()) {
                throw new BizException(ErrorCode.CRITERIA_UNJUDGED,
                        "验收条件还有 " + (criteria.size() - (cmd.criteriaChecks() == null ? 0 : cmd.criteriaChecks().size()))
                                + " 项未判定，逐条勾选后再提交");
            }
        }
        TaskStatus before = task.status;
        task.status = TaskStatus.SUBMITTED;
        task.resultSummary = summary;
        taskRepository.update(task);
        audit(actor, "STATUS", id, Map.of("status", before.name()),
                Map.of("status", task.status.name(), "resultSummary", summary,
                        "criteriaChecks", cmd.criteriaChecks() == null ? "" : cmd.criteriaChecks().toString()));
        return detail(id, actor);
    }

    @Transactional
    public TaskOperations.VerifyResult verify(Long id, VerifyCommand cmd, AuthUser actor) {
        Task task = visibleTask(id, actor);
        boolean ownerLike = Objects.equals(actor.employeeId(), task.ownerId) || isManagerLike(actor);
        if (!ownerLike) {
            throw new BizException(ErrorCode.NO_PERMISSION, "只有负责人或管理者可以验收");
        }
        TaskStateMachine.assertVerifyAllowed(task.status);
        String decision = requireText(cmd.decision(), "请选择验收结论").toUpperCase();
        if (!decision.equals("PASS") && !decision.equals("REJECT")) {
            throw BizException.invalid("验收结论只能是 PASS 或 REJECT");
        }
        List<String> criteria = TaskJson.strings(task.acceptCriteria);
        if (!criteria.isEmpty() && cmd.criteriaChecks() != null && cmd.criteriaChecks().size() != criteria.size()) {
            throw new BizException(ErrorCode.CRITERIA_UNJUDGED, "验收条件还有未判定项");
        }
        TaskStatus before = task.status;
        String summary = cmd.resultSummary() == null ? null : cmd.resultSummary().trim();
        if (decision.equals("PASS")) {
            TaskStateMachine.assertPassInput(summary);
            task.status = TaskStatus.DONE;
            task.actualEnd = LocalDateTime.now();
            task.progress = 100;
            task.closureLevel = ClosureLevel.STRONG;
            task.resultSummary = summary;
            taskRepository.update(task);
            audit(actor, "STATUS", id, Map.of("status", before.name()),
                    Map.of("status", task.status.name(), "actualEnd", String.valueOf(task.actualEnd),
                            "resultSummary", summary, "closureLevel", task.closureLevel.name()));
            return new TaskOperations.VerifyResult(task.status.name(), task.actualEnd,
                    task.rejectValue(), task.delayValue());
        }

        TaskStateMachine.assertRejectInput(cmd.rejectReason());
        task.status = TaskStatus.IN_PROGRESS;
        task.rejectCount = task.rejectValue() + 1;
        if (summary != null && !summary.isBlank()) {
            task.resultSummary = summary;
        }
        taskRepository.update(task);

        TaskCheckin checkin = new TaskCheckin();
        checkin.taskId = id;
        checkin.employeeId = actor.employeeId();
        checkin.checkDate = LocalDate.now();
        checkin.progress = task.progressValue();
        checkin.content = "验收退回：" + cmd.rejectReason().trim();
        checkin.resultType = CheckinResultType.REVIEW;
        checkin.closureLevel = ClosureLevel.WEAK;
        checkinRepository.insert(checkin);

        audit(actor, "STATUS", id, Map.of("status", before.name()),
                Map.of("status", task.status.name(), "rejectCount", String.valueOf(task.rejectCount),
                        "rejectReason", cmd.rejectReason().trim()));
        return new TaskOperations.VerifyResult(task.status.name(), task.actualEnd,
                task.rejectValue(), task.delayValue());
    }

    @Transactional
    public TaskView reschedule(Long id, RescheduleCommand cmd, AuthUser actor) {
        Task task = visibleTask(id, actor);
        boolean isOwner = Objects.equals(actor.employeeId(), task.ownerId);
        boolean isAssignee = Objects.equals(actor.employeeId(), task.primaryAssigneeId);
        if (!isOwner && !isAssignee && !isManagerLike(actor)) {
            throw new BizException(ErrorCode.NO_PERMISSION, "只有主责、负责人或管理者可以改期");
        }
        TaskStateMachine.assertRescheduleAllowed(task.status);
        if (cmd.newPlanEnd() == null) {
            throw BizException.invalid("请填写新的计划完成时间");
        }
        if (cmd.oldPlanEnd() != null && task.planEnd != null
                && !task.planEnd.equals(cmd.oldPlanEnd())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, "期限已被他人修改，请刷新后重试");
        }
        if (cmd.newPlanEnd().isBefore(LocalDateTime.now())) {
            throw BizException.invalid("新的计划完成时间不能早于当前时间");
        }
        if (task.planEnd != null && cmd.newPlanEnd().equals(task.planEnd)) {
            throw BizException.invalid("新期限与当前期限相同，无需改期");
        }
        RescheduleReason reason = parseReason(cmd.reason());
        boolean toLater = task.planEnd == null || cmd.newPlanEnd().isAfter(task.planEnd);
        int limit = sysConfigRepository.getInt("task.reschedule.limit", 3);
        TaskStateMachine.assertReschedule(cmd.reasonText(), toLater, isOwner || isManagerLike(actor),
                task.delayValue(), limit, cmd.rootCause(), cmd.measure());

        LocalDateTime oldPlanEnd = task.planEnd;
        TaskCheckin checkin = new TaskCheckin();
        checkin.taskId = id;
        checkin.employeeId = actor.employeeId();
        checkin.checkDate = LocalDate.now();
        checkin.progress = task.progressValue();
        checkin.content = "改期（" + reason.label() + "）：" + cmd.reasonText().trim()
                + (cmd.measure() == null || cmd.measure().isBlank() ? "" : "；新措施：" + cmd.measure().trim());
        checkin.planEndSnapshot = oldPlanEnd;
        checkin.resultType = CheckinResultType.PROGRESS;
        checkin.closureLevel = ClosureLevel.WEAK;
        checkinRepository.insert(checkin);

        task.planEnd = cmd.newPlanEnd();
        task.delayCount = task.delayValue() + 1;
        taskRepository.update(task);
        audit(actor, "UPDATE", id, Map.of("planEnd", String.valueOf(oldPlanEnd)),
                Map.of("action", "RESCHEDULE", "planEnd", String.valueOf(task.planEnd),
                        "delayCount", String.valueOf(task.delayCount), "reason", reason.name()));
        return toView(task, actor, deptNameMap(), employeeNameMap(), assigneeRepository.listByTask(id));
    }

    @Transactional
    public TaskOperations.UrgeResult urge(Long id, AuthUser actor) {
        Task task = visibleTask(id, actor);
        boolean ownerLike = Objects.equals(actor.employeeId(), task.ownerId) || isManagerLike(actor);
        if (!ownerLike) {
            throw new BizException(ErrorCode.NO_PERMISSION, "只有负责人或管理者可以催办");
        }
        TaskStateMachine.assertUrgeAllowed(task.status);
        int windowHours = sysConfigRepository.getInt("task.urge.window.hours", 24);
        LocalDateTime last = auditLogRepository.lastActionAt(BIZ_TASK, id, "URGE");
        if (last != null && last.plusHours(windowHours).isAfter(LocalDateTime.now())) {
            throw new BizException(ErrorCode.DUPLICATE_REQUEST,
                    "同一工单 " + windowHours + " 小时内只能催办一次，上次是 " + last);
        }
        task.urgeCount = task.urgeValue() + 1;
        taskRepository.update(task);
        audit(actor, "URGE", id, null, Map.of("urgeCount", String.valueOf(task.urgeCount)));
        return new TaskOperations.UrgeResult(task.urgeCount, "已提醒主责执行人，逾期仍会按天升级");
    }

    @Transactional
    public TaskView cancel(Long id, String reason, AuthUser actor) {
        Task task = visibleTask(id, actor);
        boolean ownerLike = Objects.equals(actor.employeeId(), task.ownerId) || isManagerLike(actor);
        if (!ownerLike) {
            throw new BizException(ErrorCode.NO_PERMISSION, "只有负责人或管理者可以取消工单");
        }
        if (task.status != TaskStatus.DRAFT && task.status != TaskStatus.PENDING) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED,
                    "当前状态为「" + task.status.label() + "」，只有草稿与待接单可以取消");
        }
        TaskStatus before = task.status;
        task.status = TaskStatus.CANCELLED;
        taskRepository.update(task);
        audit(actor, "STATUS", id, Map.of("status", before.name()),
                Map.of("status", task.status.name(), "reason", reason == null ? "" : reason));
        return toView(task, actor, deptNameMap(), employeeNameMap(), assigneeRepository.listByTask(id));
    }

    @Transactional
    public TaskDetailView closeIssue(Long id, Long issueId, AuthUser actor) {
        Task task = visibleTask(id, actor);
        if (!canWriteCheckin(task, actor)) {
            throw new BizException(ErrorCode.NO_PERMISSION, "只有主责、协办或负责人可以关闭问题");
        }
        TaskIssue issue = issueRepository.findById(issueId);
        if (issue == null || !Objects.equals(issue.taskId, id)) {
            throw BizException.notFound("问题不存在或不属于该工单");
        }
        if (issue.status == IssueStatus.OPEN) {
            issueRepository.close(issueId, LocalDateTime.now());
            audit(actor, "UPDATE", id, Map.of("issueId", String.valueOf(issueId), "status", IssueStatus.OPEN.name()),
                    Map.of("issueId", String.valueOf(issueId), "status", IssueStatus.CLOSED.name()));
        }
        return detail(id, actor);
    }

    @Transactional
    public TaskDetailView doneAction(Long id, Long actionId, AuthUser actor) {
        Task task = visibleTask(id, actor);
        if (!canWriteCheckin(task, actor)) {
            throw new BizException(ErrorCode.NO_PERMISSION, "只有主责、协办或负责人可以标记措施完成");
        }
        TaskAction action = actionRepository.findById(actionId);
        if (action == null || !Objects.equals(action.taskId, id)) {
            throw BizException.notFound("措施不存在或不属于该工单");
        }
        if (action.status == ActionStatus.OPEN) {
            actionRepository.markDone(actionId);
            audit(actor, "UPDATE", id, Map.of("actionId", String.valueOf(actionId), "status", ActionStatus.OPEN.name()),
                    Map.of("actionId", String.valueOf(actionId), "status", ActionStatus.DONE.name()));
        }
        return detail(id, actor);
    }

    // ---------------------------------------------------------------- 视图与规则辅助

    private TaskView toView(Task task, AuthUser actor, Map<Long, String> deptNames,
                            Map<Long, String> employeeNames, List<TaskAssignee> assignees) {
        int overdueDays = task.status.isOpen() ? TaskStateMachine.overdueDays(task.planEnd, LocalDateTime.now()) : 0;
        OverdueLevel level = OverdueLevel.of(overdueDays);
        boolean isOwnerActor = Objects.equals(actor.employeeId(), task.ownerId);
        boolean isAssigneeActor = Objects.equals(actor.employeeId(), task.primaryAssigneeId);
        boolean isCoAssignee = assignees != null && assignees.stream()
                .anyMatch(a -> Objects.equals(a.employeeId, actor.employeeId()));
        boolean managerLike = isManagerLike(actor);
        boolean ownerLike = isOwnerActor || managerLike;
        TaskView.Actions actions = new TaskView.Actions(
                dispatchAllowed(task.status),
                task.status.isWorkable() && (isAssigneeActor || isOwnerActor || isCoAssignee || hasRole(actor, ROLE_ADMIN)),
                task.status == TaskStatus.IN_PROGRESS && (isAssigneeActor || hasRole(actor, ROLE_ADMIN)),
                task.status == TaskStatus.SUBMITTED && ownerLike,
                rescheduleAllowed(task.status) && (isAssigneeActor || isOwnerActor || managerLike),
                task.status.isOpen() && task.status != TaskStatus.SUBMITTED && ownerLike,
                ownerLike && (task.status == TaskStatus.DRAFT || task.status == TaskStatus.PENDING));
        return new TaskView(
                task.id, task.taskNo, task.title,
                task.type == null ? null : task.type.name(),
                task.type == null ? null : task.type.label(),
                task.status.name(), task.status.label(),
                task.deptId, deptNames.get(task.deptId),
                task.ownerId, employeeNames.get(task.ownerId),
                task.primaryAssigneeId, employeeNames.get(task.primaryAssigneeId),
                task.assigneeText, task.priority,
                task.planStart, task.planEnd, task.actualEnd,
                task.progressValue(), overdueDays > 0, overdueDays,
                level.name(), level.label(),
                task.recurrenceOf, task.delayValue(), task.rejectValue(), task.urgeValue(),
                task.closureLevel == null ? null : task.closureLevel.name(),
                task.createdAt, actions);
    }

    private boolean dispatchAllowed(TaskStatus status) {
        return status == TaskStatus.DRAFT || status == TaskStatus.PENDING
                || status == TaskStatus.IN_PROGRESS || status == TaskStatus.BLOCKED;
    }

    private boolean rescheduleAllowed(TaskStatus status) {
        return status == TaskStatus.PENDING || status == TaskStatus.IN_PROGRESS || status == TaskStatus.BLOCKED;
    }

    private List<String> submitGaps(Task task, List<TaskIssue> issues) {
        List<String> gaps = new ArrayList<>();
        if (task.status != TaskStatus.IN_PROGRESS) {
            gaps.add("当前状态为「" + task.status.label() + "」，提交前要处于进行中");
        }
        int openIssues = Math.toIntExact(issues.stream().filter(i -> i.status == IssueStatus.OPEN).count());
        if (openIssues > 0) {
            gaps.add("问题 " + openIssues + " 条未关");
        }
        int resultRecords = checkinRepository.countByResultType(task.id, CheckinResultType.RESULT);
        if (resultRecords == 0) {
            gaps.add("结果类追踪记录 0 条");
        }
        return gaps;
    }

    private Task visibleTask(Long id, AuthUser actor) {
        Task task = taskRepository.findById(id);
        if (task == null) {
            throw BizException.notFound("工单不存在或已删除");
        }
        TaskScope scope = scopeOf(actor);
        if (scope.deptIds() != null && !scope.deptIds().contains(task.deptId)) {
            throw new BizException(ErrorCode.OUT_OF_SCOPE, "该工单不在你的数据范围内");
        }
        if (scope.selfOnly() && !isRelated(task, actor)) {
            throw new BizException(ErrorCode.OUT_OF_SCOPE, "该工单不在你的数据范围内");
        }
        return task;
    }

    private boolean isRelated(Task task, AuthUser actor) {
        if (Objects.equals(actor.employeeId(), task.ownerId)
                || Objects.equals(actor.employeeId(), task.primaryAssigneeId)) {
            return true;
        }
        return assigneeRepository.exists(task.id, actor.employeeId());
    }

    private boolean canWriteCheckin(Task task, AuthUser actor) {
        return Objects.equals(actor.employeeId(), task.ownerId)
                || Objects.equals(actor.employeeId(), task.primaryAssigneeId)
                || assigneeRepository.exists(task.id, actor.employeeId())
                || hasRole(actor, ROLE_ADMIN);
    }

    private TaskScope scopeOf(AuthUser actor) {
        return DataScopeResolver.resolve(actor, deptRepository.findAll());
    }

    private void assertDeptInScope(Long deptId, TaskScope scope) {
        if (scope.deptIds() != null && !scope.deptIds().contains(deptId)) {
            throw new BizException(ErrorCode.OUT_OF_SCOPE, "所选部门不在你的数据范围内");
        }
    }

    private void requireOwnerAboveDept(Employee owner, Dept dept) {
        Dept ownerDept = deptRepository.findById(owner.getDeptId());
        if (ownerDept == null || !DeptTree.isSelfOrDescendant(ownerDept.getPath(), dept.getPath())) {
            throw new BizException(ErrorCode.RULE_UNSATISFIED, "负责人须属于承办部门或其上级部门");
        }
    }

    private void requireAssigneeUnderDept(Employee assignee, Dept dept) {
        if (dept == null) {
            throw BizException.notFound("承办部门不存在，请刷新后重试");
        }
        Dept assigneeDept = deptRepository.findById(assignee.getDeptId());
        if (assigneeDept == null || !DeptTree.isSelfOrDescendant(dept.getPath(), assigneeDept.getPath())) {
            throw new BizException(ErrorCode.RULE_UNSATISFIED, "主责执行人须属于承办部门或其下级部门");
        }
    }

    private Employee requireActiveEmployee(Long employeeId, String missingMessage) {
        if (employeeId == null) {
            throw BizException.invalid(missingMessage);
        }
        Employee employee = employeeRepository.findById(employeeId);
        if (employee == null) {
            throw BizException.notFound("人员不存在，请刷新后重试");
        }
        if (employee.getStatus() == null || employee.getStatus() != 1) {
            throw new BizException(ErrorCode.RULE_UNSATISFIED, "所选人员不在职，不能指派");
        }
        return employee;
    }

    private Task findSameTitleOpenTask(Long deptId, String title) {
        String normalized = title.trim();
        return taskRepository.findOpenByDept(deptId).stream()
                .filter(t -> TitleSimilarity.score(t.title, normalized) >= 0.999)
                .findFirst()
                .orElse(null);
    }

    private Long findRecurrence(Long deptId, String title) {
        double threshold = sysConfigRepository.getDouble("similarity.threshold", 0.85);
        int windowDays = sysConfigRepository.getInt("similarity.window.days", 180);
        LocalDateTime since = LocalDateTime.now().minusDays(windowDays);
        List<Task> history = taskRepository.findDoneSince(deptId, since);
        Task best = null;
        double bestScore = 0;
        for (Task old : history) {
            double score = TitleSimilarity.score(old.title, title);
            if (score >= threshold && score > bestScore) {
                best = old;
                bestScore = score;
            }
        }
        return best == null ? null : best.id;
    }

    private void insertCoAssignees(Task task, List<Long> employeeIds, Long excludeId) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return;
        }
        for (Long employeeId : employeeIds) {
            if (employeeId == null || Objects.equals(employeeId, excludeId)
                    || assigneeRepository.exists(task.id, employeeId)) {
                continue;
            }
            Employee employee = requireActiveEmployee(employeeId, "请选择协办人");
            TaskAssignee assignee = new TaskAssignee();
            assignee.taskId = task.id;
            assignee.employeeId = employee.getId();
            assignee.roleType = AssigneeRoleType.ASSIST;
            assigneeRepository.insert(assignee);
        }
    }

    private long count(TaskScope scope, TaskStatus status, boolean mine, boolean overdueOnly) {
        TaskQuery query = new TaskQuery(null, status, null, null, null, null, mine,
                overdueOnly, false, LocalDateTime.now(), scope, 1, 1);
        return taskRepository.count(query);
    }

    private void audit(AuthUser actor, String action, Long taskId, Map<String, Object> before,
                       Map<String, Object> after) {
        AuditLog log = new AuditLog();
        log.actorId = actor.employeeId();
        log.action = action;
        log.bizType = BIZ_TASK;
        log.bizId = taskId;
        log.before = TaskJson.snapshot(before);
        log.after = TaskJson.snapshot(after);
        auditLogRepository.insert(log);
    }

    private void requireAnyRole(AuthUser actor, String... roleCodes) {
        for (String code : roleCodes) {
            if (actor.roles().contains(code)) {
                return;
            }
        }
        throw new BizException(ErrorCode.NO_PERMISSION, "当前角色不能执行该操作，请联系管理者");
    }

    private boolean hasRole(AuthUser actor, String roleCode) {
        return actor.roles().contains(roleCode);
    }

    private boolean isManagerLike(AuthUser actor) {
        return hasRole(actor, ROLE_MANAGER) || hasRole(actor, ROLE_ADMIN);
    }

    private String requireTitle(String value) {
        String title = requireText(value, "请填写工单标题");
        if (title.length() < 2 || title.length() > 120) {
            throw BizException.invalid("标题长度 2 到 120 字");
        }
        return title;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw BizException.invalid(message);
        }
        return value.trim();
    }

    private TaskStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TaskStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BizException.invalid("状态取值不合法：" + value);
        }
    }

    private TaskType parseType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TaskType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BizException.invalid("工单类型不合法：" + value);
        }
    }

    private TaskSourceType parseSourceType(String value) {
        if (value == null || value.isBlank()) {
            return TaskSourceType.MANUAL;
        }
        try {
            return TaskSourceType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TaskSourceType.MANUAL;
        }
    }

    private CheckinResultType parseResultType(String value) {
        if (value == null || value.isBlank()) {
            throw BizException.invalid("请选择记录类型：进展、结果、问题或验收意见");
        }
        try {
            return CheckinResultType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BizException.invalid("记录类型不合法：" + value);
        }
    }

    private RescheduleReason parseReason(String value) {
        if (value == null || value.isBlank()) {
            throw BizException.invalid("请选择改期原因");
        }
        try {
            return RescheduleReason.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BizException.invalid("改期原因只能是 EXTERNAL、CHANGE、STAFFING、ESTIMATE");
        }
    }

    private Map<Long, String> deptNameMap() {
        return deptRepository.findAll().stream()
                .collect(Collectors.toMap(d -> d.getId(), d -> d.getName(), (a, b) -> a, LinkedHashMap::new));
    }

    private Map<Long, String> employeeNameMap() {
        return employeeRepository.findAll().stream()
                .collect(Collectors.toMap(e -> e.getId(), e -> e.getName(), (a, b) -> a, LinkedHashMap::new));
    }
}
