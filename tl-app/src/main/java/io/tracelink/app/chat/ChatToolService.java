package io.tracelink.app.chat;

import io.tracelink.app.stats.KpiView;
import io.tracelink.app.stats.PersonStat;
import io.tracelink.app.stats.StatsAppService;
import io.tracelink.app.stats.StatsOverviewView;
import io.tracelink.app.stats.StatsPersonView;
import io.tracelink.app.task.TaskAppService;
import io.tracelink.app.task.TaskView;
import io.tracelink.common.api.PageResult;
import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.org.model.Dept;
import io.tracelink.domain.org.model.Employee;
import io.tracelink.domain.org.repository.DeptRepository;
import io.tracelink.domain.org.repository.EmployeeRepository;
import io.tracelink.domain.org.rules.DataScopeResolver;
import io.tracelink.domain.task.model.TaskQuery;
import io.tracelink.domain.task.model.TaskScope;
import io.tracelink.domain.task.model.TaskType;
import io.tracelink.domain.task.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 四类只读工具的确定性路由（DESIGN 4.8）。模型不产出 SQL，也不给写接口；
 * 关键词只决定「查哪一类」，真正的过滤条件由应用服务按调用人数据范围注入。
 * 越权的部门或人员输入一律裁剪为调用人范围，不报错，避免把范围边界当错误暴露。
 */
@Service
public class ChatToolService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int TASK_LIMIT = 10;
    private static final int EMPLOYEE_LIMIT = 5;

    private final TaskAppService taskAppService;
    private final StatsAppService statsAppService;
    private final TaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;
    private final DeptRepository deptRepository;

    public ChatToolService(TaskAppService taskAppService,
                           StatsAppService statsAppService,
                           TaskRepository taskRepository,
                           EmployeeRepository employeeRepository,
                           DeptRepository deptRepository) {
        this.taskAppService = taskAppService;
        this.statsAppService = statsAppService;
        this.taskRepository = taskRepository;
        this.employeeRepository = employeeRepository;
        this.deptRepository = deptRepository;
    }

    public List<ChatToolResult> run(AuthUser actor, ChatAskCommand command) {
        String question = command == null || command.question() == null ? "" : command.question();
        if (question.isBlank()) {
            return List.of();
        }
        TaskScope scope = DataScopeResolver.resolve(actor, deptRepository.findAll());
        List<ChatToolResult> results = new ArrayList<>();
        if (needsTask(question)) {
            results.add(taskTool(actor, question, scope));
        }
        if (needsStats(question)) {
            results.add(statsTool(actor, question, scope));
        }
        if (needsEmployee(question)) {
            results.add(employeeTool(actor, question, scope));
        }
        return results;
    }

    // ---------------------------------------------------------------- 工单

    private ChatToolResult taskTool(AuthUser actor, String question, TaskScope scope) {
        long start = System.currentTimeMillis();
        boolean clamped = false;
        List<Long> allowed = deptScope(actor, scope);
        Long requested = deptInQuestion(question);
        Long deptId = null;
        if (requested != null) {
            if (allowed == null || allowed.contains(requested)) {
                deptId = requested;
            } else {
                clamped = true;
            }
        }
        boolean overdue = question.contains("逾期") || question.contains("超期");
        String status = null;
        if (question.contains("未验收") || question.contains("待验收") || question.contains("没验收")) {
            status = "SUBMITTED";
        } else if (question.contains("待接单")) {
            status = "PENDING";
        } else if (question.contains("进行中")) {
            status = "IN_PROGRESS";
        } else if (question.contains("阻碍") || question.contains("阻塞")) {
            status = "BLOCKED";
        } else if (question.contains("已完成") || question.contains("验收完")) {
            status = "DONE";
        }
        TaskType type = typeInQuestion(question);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("deptId", deptId);
        args.put("status", status);
        args.put("type", type == null ? null : type.name());
        args.put("overdueOnly", overdue);
        args.put("clamped", clamped);
        args.put("limit", TASK_LIMIT);

        PageResult<TaskView> page = taskAppService.page(actor, null, status,
                type == null ? null : type.name(), deptId, null, null, false, overdue, false,
                1, TASK_LIMIT);
        List<String> facts = page.records().stream()
                .map(t -> t.taskNo() + " " + t.title() + "（" + t.statusLabel() + "，期限 "
                        + (t.planEnd() == null ? "未定" : t.planEnd().toLocalDate().format(DAY))
                        + (t.primaryAssigneeName() == null ? "" : "，主责 " + t.primaryAssigneeName()) + "）")
                .toList();
        return new ChatToolResult("task_query", "查工单", args, (int) page.total(),
                System.currentTimeMillis() - start, facts);
    }

    // ---------------------------------------------------------------- 统计

    private ChatToolResult statsTool(AuthUser actor, String question, TaskScope scope) {
        long start = System.currentTimeMillis();
        LocalDate to = LocalDate.now();
        LocalDate from;
        if (question.contains("年")) {
            from = to.withDayOfYear(1);
        } else if (question.contains("月")) {
            from = to.withDayOfMonth(1);
        } else if (question.contains("周")) {
            from = to.minusDays(6);
        } else {
            from = to.minusDays(29);
        }
        boolean clamped = false;
        List<Long> allowed = deptScope(actor, scope);
        Long deptId = null;
        Long requested = deptInQuestion(question);
        if (requested != null) {
            if (allowed == null || allowed.contains(requested)) {
                deptId = requested;
            } else {
                clamped = true;
            }
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("from", from.format(DAY));
        args.put("to", to.format(DAY));
        args.put("clamped", clamped);
        // SELF 范围只返回本人的聚合数字，避免部门聚合把他人数据带出去
        if (scope.selfOnly()) {
            StatsPersonView personView = statsAppService.computePerson(from, to, actor.deptId(),
                    actor.employeeId());
            List<String> facts = new ArrayList<>();
            for (PersonStat person : personView.persons()) {
                facts.add("新建 " + person.created() + " 单");
                facts.add("应完成 " + person.due() + " 单，已完成 " + person.done() + " 单");
                facts.add("在办 " + person.open() + " 单，其中逾期 " + person.overdue() + " 单");
                facts.add("按时完成率：" + format(person.onTimeRate()) + "%");
                facts.add("结果留痕率：" + format(person.evidenceRate()) + "%");
            }
            args.put("employeeId", actor.employeeId());
            args.put("deptId", actor.deptId());
            return new ChatToolResult("stats_query", "查统计",
                    args, facts.size(), System.currentTimeMillis() - start, facts);
        }
        // 统计聚合只按单个部门取：调用人不是全域范围时，一律收回可见部门，避免空参数变成全公司口径
        Long statDept = allowed == null ? deptId : (deptId != null ? deptId : actor.deptId());
        StatsOverviewView view = statsAppService.computeOverview(from, to, statDept);
        List<String> facts = view.kpis().stream()
                .limit(6)
                .map(k -> k.label() + "：" + format(k.value()) + (k.unit() == null ? "" : k.unit()))
                .toList();
        args.put("deptId", statDept);
        return new ChatToolResult("stats_query", "查统计",
                args, view.kpis().size(), System.currentTimeMillis() - start, facts);
    }

    // ---------------------------------------------------------------- 人员

    private ChatToolResult employeeTool(AuthUser actor, String question, TaskScope scope) {
        long start = System.currentTimeMillis();
        boolean clamped = false;
        List<Long> allowed = deptScope(actor, scope);
        Long requested = deptInQuestion(question);
        Long deptId = null;
        if (requested != null) {
            if (allowed == null || allowed.contains(requested)) {
                deptId = requested;
            } else {
                clamped = true;
            }
        }
        Map<Long, String> deptNames = new LinkedHashMap<>();
        for (Dept dept : deptRepository.findAll()) {
            deptNames.put(dept.getId(), dept.getName());
        }
        Long deptFilter = deptId;
        List<Employee> visible = employeeRepository.findAll().stream()
                .filter(e -> e.getStatus() == null || e.getStatus() == 1)
                .filter(e -> scope.selfOnly() ? Objects.equals(actor.employeeId(), e.getId()) : true)
                .filter(e -> allowed == null || allowed.contains(e.getDeptId()))
                .filter(e -> deptFilter == null || Objects.equals(deptFilter, e.getDeptId()))
                .toList();
        List<Employee> named = visible.stream()
                .filter(e -> e.getName() != null && e.getName().length() >= 2 && question.contains(e.getName()))
                .toList();
        List<Employee> targets;
        if (!named.isEmpty()) {
            targets = named.stream().limit(EMPLOYEE_LIMIT).toList();
        } else {
            LocalDateTime now = LocalDateTime.now();
            targets = visible.stream()
                    .sorted(Comparator.comparingLong(e -> -loadOf(e.getId(), scope, now)))
                    .limit(EMPLOYEE_LIMIT)
                    .toList();
        }
        LocalDateTime now = LocalDateTime.now();
        List<String> facts = targets.stream()
                .map(e -> e.getName() + "（" + deptNames.getOrDefault(e.getDeptId(), "未分配部门") + "）在办 "
                        + loadOf(e.getId(), scope, now) + " 单")
                .toList();
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("deptId", deptId);
        args.put("names", named.stream().map(Employee::getName).toList());
        args.put("clamped", clamped);
        args.put("limit", EMPLOYEE_LIMIT);
        return new ChatToolResult("employee_query", "查人员",
                args, targets.size(), System.currentTimeMillis() - start, facts);
    }

    private long loadOf(Long employeeId, TaskScope scope, LocalDateTime now) {
        return taskRepository.count(new TaskQuery(null, null, null, null, null, employeeId,
                false, false, false, now, scope, 1, 1));
    }

    // ---------------------------------------------------------------- 路由

    private boolean needsTask(String question) {
        return containsAny(question, "工单", "单子", "任务", "待办", "验收", "派工", "进度",
                "期限", "逾期", "超期", "阻碍", "阻塞", "几张", "多少单");
    }

    private boolean needsStats(String question) {
        return containsAny(question, "统计", "完成率", "按时率", "逾期率", "趋势", "指标",
                "对比", "效率", "多少");
    }

    private boolean needsEmployee(String question) {
        return containsAny(question, "谁", "人员", "负载", "负荷", "在办", "忙", "执行人", "负责人");
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 工具可用的部门范围：ALL 为 null（不收窄）；DEPT/DEPT_TREE 取换算后的部门列表；
     * SELF 只允许本人部门，避免空部门参数被当成全公司口径。
     */
    private List<Long> deptScope(AuthUser actor, TaskScope scope) {
        if (scope.deptIds() != null) {
            return scope.deptIds();
        }
        if (scope.selfOnly()) {
            return actor.deptId() == null ? List.of() : List.of(actor.deptId());
        }
        return null;
    }

    private Long deptInQuestion(String question) {
        Long matched = null;
        String longest = "";
        for (Dept dept : deptRepository.findAll()) {
            String name = dept.getName();
            if (name != null && name.length() >= 2 && question.contains(name) && name.length() > longest.length()) {
                longest = name;
                matched = dept.getId();
            }
        }
        return matched;
    }

    private TaskType typeInQuestion(String question) {
        for (TaskType type : TaskType.values()) {
            if (question.contains(type.label())) {
                return type;
            }
        }
        return null;
    }

    private String format(double value) {
        return Math.abs(value - Math.round(value)) < 0.01
                ? String.valueOf((long) Math.round(value)) : String.format("%.2f", value);
    }
}
