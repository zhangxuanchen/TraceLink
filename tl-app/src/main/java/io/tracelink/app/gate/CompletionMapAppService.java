package io.tracelink.app.gate;

import io.tracelink.common.api.PageResult;
import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.gate.model.GateCell;
import io.tracelink.domain.gate.model.GateCode;
import io.tracelink.domain.gate.model.GateRule;
import io.tracelink.domain.gate.model.GateState;
import io.tracelink.domain.gate.model.Notification;
import io.tracelink.domain.gate.model.TaskGateSnapshot;
import io.tracelink.domain.gate.repository.GateSnapshotRepository;
import io.tracelink.domain.gate.repository.NotificationRepository;
import io.tracelink.domain.org.model.Dept;
import io.tracelink.domain.org.repository.DeptRepository;
import io.tracelink.domain.org.repository.EmployeeRepository;
import io.tracelink.domain.org.rules.DataScopeResolver;
import io.tracelink.domain.org.rules.DeptTree;
import io.tracelink.domain.stat.repository.TaskMetricRepository;
import io.tracelink.domain.task.model.Task;
import io.tracelink.domain.task.model.TaskAssignee;
import io.tracelink.domain.task.model.TaskScope;
import io.tracelink.domain.task.repository.TaskActionRepository;
import io.tracelink.domain.task.repository.TaskAssigneeRepository;
import io.tracelink.domain.task.repository.TaskCheckinRepository;
import io.tracelink.domain.task.repository.TaskIssueRepository;
import io.tracelink.domain.task.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 完成地图应用服务：单工单实时算式、部门缺口矩阵、缺口排行与事项视图。
 * 判定口径在同包的 GateEvaluator，快照与提醒在 GateSnapshotService，这里只做装配、权限与分页。
 */
@Service
public class CompletionMapAppService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_MATRIX_ROWS = 200;
    private static final int MAX_MATTER_TASKS = 50;

    private final TaskRepository taskRepository;
    private final TaskIssueRepository issueRepository;
    private final TaskActionRepository actionRepository;
    private final TaskCheckinRepository checkinRepository;
    private final TaskAssigneeRepository assigneeRepository;
    private final TaskMetricRepository metricRepository;
    private final DeptRepository deptRepository;
    private final EmployeeRepository employeeRepository;
    private final GateSnapshotRepository snapshotRepository;
    private final NotificationRepository notificationRepository;
    private final GateEvaluator evaluator;
    private final GateSnapshotService snapshotService;

    public CompletionMapAppService(TaskRepository taskRepository,
                                   TaskIssueRepository issueRepository,
                                   TaskActionRepository actionRepository,
                                   TaskCheckinRepository checkinRepository,
                                   TaskAssigneeRepository assigneeRepository,
                                   TaskMetricRepository metricRepository,
                                   DeptRepository deptRepository,
                                   EmployeeRepository employeeRepository,
                                   GateSnapshotRepository snapshotRepository,
                                   NotificationRepository notificationRepository,
                                   GateEvaluator evaluator,
                                   GateSnapshotService snapshotService) {
        this.taskRepository = taskRepository;
        this.issueRepository = issueRepository;
        this.actionRepository = actionRepository;
        this.checkinRepository = checkinRepository;
        this.assigneeRepository = assigneeRepository;
        this.metricRepository = metricRepository;
        this.deptRepository = deptRepository;
        this.employeeRepository = employeeRepository;
        this.snapshotRepository = snapshotRepository;
        this.notificationRepository = notificationRepository;
        this.evaluator = evaluator;
        this.snapshotService = snapshotService;
    }

    // ---------------------------------------------------------------- CMP-1 单工单

    public CompletionMapView map(Long taskId, AuthUser actor, boolean includeNa, String gateCodes) {
        Task task = visibleTask(taskId, actor);
        List<GateCell> cells = evaluateLive(task);
        int rate = evaluator.completionRate(cells);
        Set<GateCode> only = parseGateCodes(gateCodes);
        List<GateItem> items = cells.stream()
                .filter(c -> includeNa || c.state != GateState.NA)
                .filter(c -> only.isEmpty() || only.contains(c.code))
                .map(this::item)
                .toList();
        LocalDate last = snapshotRepository.latestDate();
        return new CompletionMapView(task.id, task.taskNo, task.title, task.status.name(), task.status.label(),
                rate, items, last == null ? null : last.format(DAY), "LIVE");
    }

    // ---------------------------------------------------------------- CMP-2 部门矩阵

    public MatrixView matrix(AuthUser actor, Long deptId, String taskType, int pageNo, int pageSize) {
        requireManagerOrOwner(actor);
        List<Dept> depts = deptRepository.findAll();
        List<Long> deptIds = deptIdsInScope(actor, deptId, depts);
        LocalDate latest = snapshotRepository.latestDate();
        boolean stale = latest == null || latest.isBefore(LocalDate.now());

        List<MatrixView.Row> rows;
        if (latest == null) {
            rows = liveMatrixRows(actor, deptIds, taskType);
        } else {
            rows = snapshotMatrixRows(actor, latest, deptIds, taskType);
        }
        List<GateRule> columns = evaluator.effectiveRules(null);
        int[] todoByColumn = new int[columns.size()];
        for (MatrixView.Row row : rows) {
            for (int i = 0; i < columns.size(); i++) {
                if (row.cells().size() > i && "TODO".equals(row.cells().get(i).state())) {
                    todoByColumn[i]++;
                }
            }
        }
        List<MatrixView.Column> columnViews = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            GateRule rule = columns.get(i);
            columnViews.add(new MatrixView.Column(rule.gateCode.name(), rule.gateCode.title(), todoByColumn[i]));
        }

        long total = rows.size();
        int from = Math.max(0, (Math.max(1, pageNo) - 1) * Math.max(1, pageSize));
        int to = Math.min(rows.size(), from + Math.max(1, pageSize));
        List<MatrixView.Row> page = from >= rows.size() ? List.of() : rows.subList(from, to);
        return new MatrixView(latest == null ? null : latest.format(DAY), stale, columnViews, page, total);
    }

    private List<MatrixView.Row> snapshotMatrixRows(AuthUser actor, LocalDate date, List<Long> deptIds,
                                                    String taskType) {
        List<TaskGateSnapshot> snapshots = snapshotRepository.findByDateAndDepts(date, deptIds);
        Map<Long, List<TaskGateSnapshot>> byTask = new LinkedHashMap<>();
        for (TaskGateSnapshot row : snapshots) {
            byTask.computeIfAbsent(row.taskId, k -> new ArrayList<>()).add(row);
        }
        List<Task> tasks = filterTasks(taskRepository.findByIds(new ArrayList<>(byTask.keySet())), actor, taskType);
        Map<Long, String> names = employeeNames();
        Map<String, List<GateRule>> rulesByType = new HashMap<>();
        List<GateRule> columns = evaluator.effectiveRules(null);
        List<MatrixView.Row> rows = new ArrayList<>();
        for (Task task : tasks) {
            Map<GateCode, TaskGateSnapshot> cells = byTask.getOrDefault(task.id, List.of()).stream()
                    .collect(Collectors.toMap(r -> r.gateCode, r -> r, (a, b) -> a));
            List<GateRule> rules = rulesByType.computeIfAbsent(typeOf(task), type -> evaluator.effectiveRules(type));
            int rate = completionRate(rules, cells);
            List<MatrixView.Cell> cellViews = new ArrayList<>();
            for (GateRule column : columns) {
                TaskGateSnapshot behind = cells.get(column.gateCode);
                GateState state = behind == null ? GateState.NA : behind.state;
                cellViews.add(new MatrixView.Cell(column.gateCode.name(), state.name(), state.label(),
                        state == GateState.TODO ? behind.gapValue() : 0));
            }
            rows.add(matrixRow(task, names.get(task.primaryAssigneeId), cellViews, rate));
        }
        rows.sort(rowComparator());
        return rows;
    }

    private List<MatrixView.Row> liveMatrixRows(AuthUser actor, List<Long> deptIds, String taskType) {
        List<Task> tasks = taskRepository.findOpenAll(MAX_MATRIX_ROWS * 2).stream()
                .filter(t -> deptIds.contains(t.deptId))
                .filter(t -> taskType == null || taskType.equalsIgnoreCase(typeOf(t)))
                .filter(t -> isVisible(t, actor))
                .limit(MAX_MATRIX_ROWS)
                .toList();
        Map<Long, String> names = employeeNames();
        List<GateRule> columns = evaluator.effectiveRules(null);
        List<MatrixView.Row> rows = new ArrayList<>();
        for (Task task : tasks) {
            List<GateCell> cells = evaluateLive(task);
            Map<GateCode, GateCell> cellMap = cells.stream()
                    .collect(Collectors.toMap(c -> c.code, c -> c, (a, b) -> a));
            List<MatrixView.Cell> cellViews = new ArrayList<>();
            for (GateRule column : columns) {
                GateCell cell = cellMap.get(column.gateCode);
                GateState state = cell == null ? GateState.NA : cell.state;
                cellViews.add(new MatrixView.Cell(column.gateCode.name(), state.name(), state.label(),
                        state == GateState.TODO ? cell.gapDays : 0));
            }
            rows.add(matrixRow(task, names.get(task.primaryAssigneeId), cellViews, evaluator.completionRate(cells)));
        }
        rows.sort(rowComparator());
        return rows;
    }

    private MatrixView.Row matrixRow(Task task, String assigneeName, List<MatrixView.Cell> cells, int rate) {
        boolean overdue = false;
        int overdueDays = 0;
        if (task.planEnd != null && task.status != null && task.status.isOpen()
                && task.planEnd.isBefore(LocalDateTime.now())) {
            overdue = true;
            overdueDays = (int) Math.max(1, Duration.between(task.planEnd, LocalDateTime.now()).toDays());
        }
        return new MatrixView.Row(task.id, task.taskNo, task.title,
                assigneeName == null ? task.assigneeText : assigneeName, overdue, overdueDays, rate, cells);
    }

    private Comparator<MatrixView.Row> rowComparator() {
        return Comparator.comparing((MatrixView.Row r) -> r.overdue() ? 0 : 1)
                .thenComparing(r -> r.overdueDays(), Comparator.reverseOrder())
                .thenComparing(MatrixView.Row::taskId);
    }

    private int completionRate(List<GateRule> rules, Map<GateCode, TaskGateSnapshot> cells) {
        int done = 0;
        int applicable = 0;
        for (GateRule rule : rules) {
            TaskGateSnapshot cell = cells.get(rule.gateCode);
            GateState state = cell == null ? GateState.NA : cell.state;
            if (state == GateState.NA) {
                continue;
            }
            applicable += rule.weightValue();
            if (state == GateState.DONE) {
                done += rule.weightValue();
            }
        }
        return applicable == 0 ? 0 : (int) Math.round(done * 100.0 / applicable);
    }

    // ---------------------------------------------------------------- CMP-3 缺口排行

    public GapRankView gaps(AuthUser actor, Long deptId) {
        requireManagerOrOwner(actor);
        List<Dept> depts = deptRepository.findAll();
        List<Long> deptIds = deptIdsInScope(actor, deptId, depts);
        LocalDate latest = snapshotRepository.latestDate();
        if (latest == null) {
            return new GapRankView(null, true, 0, List.of(), List.of(), List.of(), List.of());
        }
        List<TaskGateSnapshot> snapshots = snapshotRepository.findByDateAndDepts(latest, deptIds);
        List<Long> taskIds = snapshots.stream().map(s -> s.taskId).distinct().toList();
        Map<Long, Task> taskById = taskRepository.findByIds(taskIds).stream()
                .collect(Collectors.toMap(t -> t.id, t -> t, (a, b) -> a));
        Map<Long, String> names = employeeNames();
        List<GateRule> columns = evaluator.effectiveRules(null);

        Map<GateCode, int[]> byGate = new LinkedHashMap<>();
        Map<Long, int[]> byHolder = new LinkedHashMap<>();
        Map<Long, int[]> byDept = new LinkedHashMap<>();
        Map<Long, String> deptNames = depts.stream()
                .collect(Collectors.toMap(Dept::getId, Dept::getName, (a, b) -> a));
        List<GapRankView.TopTask> top = new ArrayList<>();
        int totalTodo = 0;
        for (TaskGateSnapshot row : snapshots) {
            if (row.state == GateState.NA) {
                continue;
            }
            Task task = taskById.get(row.taskId);
            if (task != null && task.deptId != null) {
                int[] dept = byDept.computeIfAbsent(task.deptId, k -> new int[3]);
                dept[2]++;
                if (row.state == GateState.TODO) {
                    dept[0]++;
                    dept[1] = Math.max(dept[1], row.gapValue());
                }
            }
            if (row.state != GateState.TODO) {
                continue;
            }
            totalTodo++;
            int[] gate = byGate.computeIfAbsent(row.gateCode, k -> new int[2]);
            gate[0]++;
            gate[1] = Math.max(gate[1], row.gapValue());
            Long holder = row.holderId;
            int[] person = byHolder.computeIfAbsent(holder == null ? 0L : holder, k -> new int[2]);
            person[0]++;
            person[1] = Math.max(person[1], row.gapValue());
            top.add(new GapRankView.TopTask(row.taskId, task == null ? null : task.taskNo,
                    task == null ? null : task.title, row.gateCode.name(), row.gateCode.title(),
                    row.gapValue(), names.get(holder)));
        }
        List<GapRankView.ByGate> gateViews = new ArrayList<>();
        for (GateRule column : columns) {
            int[] value = byGate.get(column.gateCode);
            gateViews.add(new GapRankView.ByGate(column.gateCode.name(), column.gateCode.title(),
                    value == null ? 0 : value[0], value == null ? 0 : value[1]));
        }
        List<GapRankView.ByHolder> holderViews = byHolder.entrySet().stream()
                .map(e -> new GapRankView.ByHolder(e.getKey() == 0L ? null : e.getKey(),
                        e.getKey() == 0L ? "未指定" : names.get(e.getKey()), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingInt(GapRankView.ByHolder::todoCount).reversed())
                .toList();
        List<GapRankView.ByDept> deptViews = byDept.entrySet().stream()
                .map(e -> new GapRankView.ByDept(e.getKey(),
                        deptNames.getOrDefault(e.getKey(), "未分配部门"),
                        e.getValue()[0], e.getValue()[2], e.getValue()[1]))
                .sorted(Comparator.comparingInt(GapRankView.ByDept::todoCount).reversed()
                        .thenComparing(Comparator.comparingInt(GapRankView.ByDept::maxGapDays).reversed()))
                .toList();
        top.sort(Comparator.comparingInt(GapRankView.TopTask::gapDays).reversed());
        if (top.size() > 20) {
            top = top.subList(0, 20);
        }
        return new GapRankView(latest.format(DAY), latest.isBefore(LocalDate.now()), totalTodo,
                gateViews, holderViews, deptViews, top);
    }

    // ---------------------------------------------------------------- CMP-4 事项地图

    public MatterMapView matter(Long taskId, AuthUser actor) {
        Task base = visibleTask(taskId, actor);
        List<Task> chain = matterChain(base, actor);
        List<MatterMapView.MatterTask> tasks = new ArrayList<>();
        Map<GateCode, Integer> stuck = new LinkedHashMap<>();
        Map<Long, String> names = employeeNames();
        for (Task task : chain) {
            List<GateCell> cells = evaluateLive(task);
            for (GateCell cell : cells) {
                if (cell.state == GateState.TODO) {
                    stuck.merge(cell.code, 1, Integer::sum);
                }
            }
            tasks.add(new MatterMapView.MatterTask(task.id, task.taskNo, task.title,
                    task.status.name(), task.status.label(),
                    names.get(task.primaryAssigneeId == null ? task.ownerId : task.primaryAssigneeId),
                    task.createdAt == null ? null : task.createdAt.format(MINUTE),
                    task.planEnd == null ? null : task.planEnd.format(MINUTE),
                    task.actualEnd == null ? null : task.actualEnd.format(MINUTE),
                    evaluator.completionRate(cells),
                    cells.stream().map(this::item).toList()));
        }
        GateCode stuckCode = stuck.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        return new MatterMapView(base.id, chain.isEmpty() ? base.title : chain.get(0).title, tasks,
                stuckCode == null ? null : stuckCode.name(),
                stuckCode == null ? null : stuckCode.title(),
                stuckCode == null ? 0 : stuck.get(stuckCode));
    }

    private List<Task> matterChain(Task base, AuthUser actor) {
        Map<Long, Task> seen = new LinkedHashMap<>();
        Deque<Task> queue = new ArrayDeque<>();
        seen.put(base.id, base);
        queue.add(base);
        while (!queue.isEmpty() && seen.size() < MAX_MATTER_TASKS) {
            Task current = queue.poll();
            List<Task> neighbors = new ArrayList<>();
            if (current.parentId != null) {
                Task parent = taskRepository.findById(current.parentId);
                if (parent != null) {
                    neighbors.add(parent);
                }
            }
            if (current.recurrenceOf != null) {
                Task previous = taskRepository.findById(current.recurrenceOf);
                if (previous != null) {
                    neighbors.add(previous);
                }
            }
            neighbors.addAll(taskRepository.findChildren(current.id));
            neighbors.addAll(taskRepository.findRecurrencesOf(current.id));
            for (Task neighbor : neighbors) {
                if (!seen.containsKey(neighbor.id) && isVisible(neighbor, actor)) {
                    seen.put(neighbor.id, neighbor);
                    queue.add(neighbor);
                }
            }
        }
        return seen.values().stream()
                .sorted(Comparator.comparing((Task t) -> t.createdAt == null ? LocalDateTime.MIN : t.createdAt)
                        .thenComparing(t -> t.id))
                .toList();
    }

    // ---------------------------------------------------------------- 快照与提醒

    public SnapshotResult snapshot(AuthUser actor, LocalDate date) {
        requireManagerOrOwner(actor);
        return snapshotService.run(date == null ? LocalDate.now() : date);
    }

    public PageResult<NotificationView> notifications(AuthUser actor, boolean unreadOnly, int limit) {
        int size = Math.min(Math.max(1, limit), 100);
        List<Notification> rows = notificationRepository.listByRecipient(actor.employeeId(), unreadOnly, size);
        List<NotificationView> views = rows.stream().map(this::notificationView).toList();
        long unread = notificationRepository.countUnread(actor.employeeId());
        return new PageResult<>(unread, 1, size, views);
    }

    public boolean readNotification(Long id, AuthUser actor) {
        if (!notificationRepository.markRead(id, actor.employeeId())) {
            throw BizException.notFound("提醒不存在或不属于当前用户");
        }
        return true;
    }

    private NotificationView notificationView(Notification n) {
        String label = switch (n.level) {
            case "NEW" -> "新缺口";
            case "FIRST" -> "滞留三日";
            case "SECOND" -> "滞留七日";
            default -> n.level;
        };
        return new NotificationView(n.id, n.taskId, n.taskNo, n.gateCode.name(), n.gateCode.title(),
                n.level, label, n.gapDays == null ? 0 : n.gapDays, n.title, n.content,
                Boolean.TRUE.equals(n.readFlag), n.createdAt == null ? null : n.createdAt.format(MINUTE));
    }

    // ---------------------------------------------------------------- 装配与权限

    private List<GateCell> evaluateLive(Task task) {
        return evaluator.evaluate(task, evaluator.effectiveRules(typeOf(task)),
                issueRepository.listByTask(task.id),
                actionRepository.listByTask(task.id),
                checkinRepository.listByTask(task.id),
                metricRepository.findByTaskIds(List.of(task.id)),
                snapshotService.previousGaps(task.id, LocalDate.now()),
                employeeNames());
    }

    private GateItem item(GateCell cell) {
        return new GateItem(cell.code.name(), cell.name, cell.state.name(), cell.state.label(),
                cell.weight == null ? 0 : cell.weight, cell.required, cell.holderId, cell.holderName,
                cell.gapDays, cell.actionUrl, cell.hint);
    }

    private static Set<GateCode> parseGateCodes(String value) {
        Set<GateCode> codes = new LinkedHashSet<>();
        if (value == null || value.isBlank()) {
            return codes;
        }
        for (String part : value.split(",")) {
            try {
                codes.add(GateCode.valueOf(part.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // 非法编码忽略，前端只传 G1 到 G9
            }
        }
        return codes;
    }

    private Task visibleTask(Long id, AuthUser actor) {
        Task task = taskRepository.findById(id);
        if (task == null) {
            throw BizException.notFound("工单不存在或已删除");
        }
        if (!isVisible(task, actor)) {
            throw new BizException(ErrorCode.OUT_OF_SCOPE, "该工单不在你的数据范围内");
        }
        return task;
    }

    private boolean isVisible(Task task, AuthUser actor) {
        TaskScope scope = DataScopeResolver.resolve(actor, deptRepository.findAll());
        if (scope.deptIds() != null && !scope.deptIds().contains(task.deptId)) {
            return false;
        }
        return !scope.selfOnly() || isRelated(task, actor);
    }

    private boolean isRelated(Task task, AuthUser actor) {
        if (Objects.equals(actor.employeeId(), task.ownerId)
                || Objects.equals(actor.employeeId(), task.primaryAssigneeId)) {
            return true;
        }
        return assigneeRepository.exists(task.id, actor.employeeId());
    }

    private List<Long> deptIdsInScope(AuthUser actor, Long deptId, List<Dept> depts) {
        TaskScope scope = DataScopeResolver.resolve(actor, depts);
        if (deptId != null) {
            Dept target = depts.stream().filter(d -> d.getId().equals(deptId)).findFirst()
                    .orElseThrow(() -> BizException.notFound("部门不存在"));
            if (scope.deptIds() != null && !scope.deptIds().contains(deptId)) {
                throw new BizException(ErrorCode.OUT_OF_SCOPE, "该部门不在你的数据范围内");
            }
            return depts.stream()
                    .filter(d -> DeptTree.isSelfOrDescendant(target.getPath(), d.getPath()))
                    .map(Dept::getId)
                    .toList();
        }
        if (scope.deptIds() != null) {
            return scope.deptIds();
        }
        return depts.stream().map(Dept::getId).toList();
    }

    private List<Task> filterTasks(List<Task> tasks, AuthUser actor, String taskType) {
        List<Task> filtered = tasks.stream()
                .filter(t -> taskType == null || taskType.equalsIgnoreCase(typeOf(t)))
                .toList();
        if (!scopeSelfOnly(actor)) {
            return filtered;
        }
        List<Long> ids = filtered.stream().map(t -> t.id).toList();
        Set<Long> relatedIds = assigneeRepository.listByTaskIds(ids).stream()
                .filter(a -> Objects.equals(a.employeeId, actor.employeeId()))
                .map(a -> a.taskId)
                .collect(Collectors.toSet());
        return filtered.stream()
                .filter(t -> Objects.equals(actor.employeeId(), t.ownerId)
                        || Objects.equals(actor.employeeId(), t.primaryAssigneeId)
                        || relatedIds.contains(t.id))
                .toList();
    }

    private boolean scopeSelfOnly(AuthUser actor) {
        return "SELF".equals(actor.dataScopeType())
                || (actor.dataScopeType() == null);
    }

    private void requireManagerOrOwner(AuthUser actor) {
        if (actor == null || !(actor.roles().contains("SYS_ADMIN") || actor.roles().contains("MANAGER")
                || actor.roles().contains("OWNER"))) {
            throw new BizException(ErrorCode.NO_PERMISSION, "完成地图仅对负责人及以上开放");
        }
    }

    private Map<Long, String> employeeNames() {
        return employeeRepository.findAll().stream()
                .collect(Collectors.toMap(e -> e.getId(), e -> e.getName(), (a, b) -> a, LinkedHashMap::new));
    }

    private String typeOf(Task task) {
        return task.type == null ? "OTHER" : task.type.name();
    }
}
