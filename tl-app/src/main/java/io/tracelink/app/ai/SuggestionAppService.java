package io.tracelink.app.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracelink.app.task.DispatchCommand;
import io.tracelink.app.task.RescheduleCommand;
import io.tracelink.app.task.TaskAppService;
import io.tracelink.app.task.TaskCreateCommand;
import io.tracelink.app.task.TaskDetailView;
import io.tracelink.app.task.TaskOperations;
import io.tracelink.app.task.TaskView;
import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.ai.model.Suggestion;
import io.tracelink.domain.ai.model.SuggestionStatus;
import io.tracelink.domain.ai.model.SuggestionTargetType;
import io.tracelink.domain.ai.model.SuggestionType;
import io.tracelink.domain.ai.repository.SuggestionRepository;
import io.tracelink.domain.audit.model.AuditLog;
import io.tracelink.domain.audit.repository.AuditLogRepository;
import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.org.model.Dept;
import io.tracelink.domain.org.model.Employee;
import io.tracelink.domain.org.repository.DeptRepository;
import io.tracelink.domain.org.repository.EmployeeRepository;
import io.tracelink.domain.org.rules.DataScopeResolver;
import io.tracelink.domain.task.model.Task;
import io.tracelink.domain.task.model.TaskAction;
import io.tracelink.domain.task.model.TaskAssignee;
import io.tracelink.domain.task.model.TaskIssue;
import io.tracelink.domain.task.model.TaskScope;
import io.tracelink.domain.task.model.TaskStatus;
import io.tracelink.domain.task.repository.SysConfigRepository;
import io.tracelink.domain.task.repository.TaskActionRepository;
import io.tracelink.domain.task.repository.TaskAssigneeRepository;
import io.tracelink.domain.task.repository.TaskIssueRepository;
import io.tracelink.domain.task.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
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
 * 五类建议与采纳（AI-1/AI-2）。建议由工单数据算出，只落待确认队列；
 * 采纳时由本服务执行确定性动作（建单走 TSK-1、改期走改期接口），模型不持有写接口（Q12、DESIGN 4.8）。
 * 每次生成与采纳都写审计（模型名、提示词版本、token、耗时、采纳人、时间与对象 ID），TC-M5-07/08。
 */
@Service
public class SuggestionAppService {

    private static final String ROLE_ADMIN = "SYS_ADMIN";
    private static final String ROLE_MANAGER = "MANAGER";
    private static final String ROLE_OWNER = "OWNER";
    private static final String BIZ_SUGGESTION = "AI_SUGGESTION";
    private static final String MODEL_RULE = "RULE-ADVISOR";
    private static final String PROMPT_VER = "m5-advisor-1";
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_OPEN = 300;
    private static final int MAX_DONE = 400;
    private static final int MAX_PER_TYPE = 6;
    private static final int MAX_CANDIDATES = 3;
    private static final int DEFAULT_HOURS = 72;

    private final SuggestionRepository suggestionRepository;
    private final TaskRepository taskRepository;
    private final TaskIssueRepository issueRepository;
    private final TaskActionRepository actionRepository;
    private final TaskAssigneeRepository assigneeRepository;
    private final EmployeeRepository employeeRepository;
    private final DeptRepository deptRepository;
    private final SysConfigRepository sysConfigRepository;
    private final AuditLogRepository auditLogRepository;
    private final TaskAppService taskAppService;
    private final ObjectMapper mapper = new ObjectMapper();

    public SuggestionAppService(SuggestionRepository suggestionRepository,
                                TaskRepository taskRepository,
                                TaskIssueRepository issueRepository,
                                TaskActionRepository actionRepository,
                                TaskAssigneeRepository assigneeRepository,
                                EmployeeRepository employeeRepository,
                                DeptRepository deptRepository,
                                SysConfigRepository sysConfigRepository,
                                AuditLogRepository auditLogRepository,
                                TaskAppService taskAppService) {
        this.suggestionRepository = suggestionRepository;
        this.taskRepository = taskRepository;
        this.issueRepository = issueRepository;
        this.actionRepository = actionRepository;
        this.assigneeRepository = assigneeRepository;
        this.employeeRepository = employeeRepository;
        this.deptRepository = deptRepository;
        this.sysConfigRepository = sysConfigRepository;
        this.auditLogRepository = auditLogRepository;
        this.taskAppService = taskAppService;
    }

    // ---------------------------------------------------------------- 生成

    @Transactional
    public SuggestionRunView run(AuthUser actor, String type) {
        requireManager(actor);
        SuggestionType filter = SuggestionType.parse(type);
        Gen gen = load(actor);
        if (filter == null || filter == SuggestionType.DISPATCH) {
            dispatchSuggestions(gen);
        }
        if (filter == null || filter == SuggestionType.ACTION) {
            actionSuggestions(gen);
        }
        if (filter == null || filter == SuggestionType.DUE_DATE) {
            dueDateSuggestions(gen);
        }
        if (filter == null || filter == SuggestionType.ESCALATE) {
            escalateSuggestions(gen);
        }
        if (filter == null || filter == SuggestionType.POLICY_GAP) {
            policyGapSuggestions(gen);
        }
        if (gen.created == 0) {
            gen.notes.add("本轮没有新增建议：数据里没有满足派工、措施、期限、升级或制度缺口规则的工单");
        }
        audit(actor, "SUGGESTION_RUN", 0L, null, Map.of(
                "created", String.valueOf(gen.created),
                "skipped", String.valueOf(gen.skipped),
                "type", filter == null ? "ALL" : filter.name()));
        return new SuggestionRunView(gen.created, gen.skipped, gen.byType, gen.notes);
    }

    private Gen load(AuthUser actor) {
        Gen gen = new Gen();
        gen.actor = actor;
        gen.scope = DataScopeResolver.resolve(actor, deptRepository.findAll());
        gen.now = LocalDateTime.now();
        gen.startedAt = System.currentTimeMillis();
        List<Task> open = taskRepository.findOpenAll(MAX_OPEN).stream()
                .filter(t -> inDept(gen.scope.deptIds(), t.deptId)).toList();
        List<Task> done = taskRepository.findDoneBetween(gen.now.minusDays(180), gen.now, null).stream()
                .filter(t -> inDept(gen.scope.deptIds(), t.deptId))
                .filter(t -> t.status == TaskStatus.DONE).toList();
        gen.open = open;
        gen.done = done;
        gen.employees = new LinkedHashMap<>();
        for (Employee employee : employeeRepository.findAll()) {
            gen.employees.put(employee.getId(), employee);
        }
        gen.deptNames = new LinkedHashMap<>();
        for (Dept dept : deptRepository.findAll()) {
            gen.deptNames.put(dept.getId(), dept.getName());
        }
        List<Long> openIds = open.stream().map(t -> t.id).toList();
        List<Long> doneIds = done.stream().map(t -> t.id).toList();
        gen.issuesByTask = groupIssues(issueRepository.listByTaskIds(openIds));
        gen.actionsByTask = groupActions(actionRepository.listByTaskIds(doneIds));
        gen.assignees = openIds.isEmpty() ? List.of() : assigneeRepository.listByTaskIds(openIds);
        return gen;
    }

    /** 派工建议：在办工单没有主责时，按部门、负载与历史完成率给三个候选 */
    private void dispatchSuggestions(Gen gen) {
        int count = 0;
        for (Task task : gen.open) {
            if (count >= MAX_PER_TYPE) {
                break;
            }
            if (task.primaryAssigneeId != null) {
                continue;
            }
            if (existsPending(SuggestionType.DISPATCH, task.id)) {
                gen.skipped++;
                continue;
            }
            List<Long> deptIds = departmentTree(task.deptId);
            List<Map<String, Object>> candidates = new ArrayList<>();
            Map<Long, int[]> workload = workload(gen, deptIds);
            for (Employee employee : gen.employees.values()) {
                if (employee.getDeptId() == null || !deptIds.contains(employee.getDeptId())) {
                    continue;
                }
                if (Objects.equals(employee.getId(), task.ownerId)) {
                    continue;
                }
                if (employee.getStatus() != null && employee.getStatus() != 1) {
                    continue;
                }
                int[] load = workload.getOrDefault(employee.getId(), new int[]{0, 0});
                double rate = load[0] + load[1] == 0 ? 0 : 100.0 * load[1] / (load[0] + load[1]);
                Map<String, Object> candidate = new LinkedHashMap<>();
                candidate.put("employeeId", employee.getId());
                candidate.put("name", employee.getName());
                candidate.put("deptId", employee.getDeptId());
                candidate.put("deptName", gen.deptNames.get(employee.getDeptId()));
                candidate.put("openCount", load[0]);
                candidate.put("doneCount", load[1]);
                candidate.put("successRate", round(rate));
                candidates.add(candidate);
            }
            candidates.sort(Comparator
                    .comparingInt((Map<String, Object> c) -> (int) c.get("openCount"))
                    .thenComparing(Comparator.comparingDouble((Map<String, Object> c) -> (double) c.get("successRate")).reversed()));
            if (candidates.size() > MAX_CANDIDATES) {
                candidates = new ArrayList<>(candidates.subList(0, MAX_CANDIDATES));
            }
            String summary = candidates.isEmpty()
                    ? "工单 " + task.taskNo + " 还没有主责执行人，当前部门没有可用候选人，建议先补人或调整部门"
                    : "工单 " + task.taskNo + " 还没有主责执行人，建议按负载从低到高优先考虑 "
                    + candidates.stream().map(c -> String.valueOf(c.get("name"))).toList();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("summary", summary);
            payload.put("candidates", candidates);
            if (!candidates.isEmpty()) {
                Map<String, Object> dispatch = new LinkedHashMap<>();
                dispatch.put("primaryAssigneeId", candidates.get(0).get("employeeId"));
                dispatch.put("coAssigneeIds", List.of());
                dispatch.put("priority", task.priority == null ? 2 : task.priority);
                dispatch.put("note", "采纳派工建议，指派 " + candidates.get(0).get("name"));
                payload.put("dispatch", dispatch);
            }
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("taskId", task.id);
            evidence.put("taskNo", task.taskNo);
            evidence.put("deptId", task.deptId);
            evidence.put("deptName", gen.deptNames.get(task.deptId));
            evidence.put("title", task.title);
            evidence.put("status", task.status.name());
            evidence.put("candidates", candidates.stream().map(c -> c.get("employeeId")).toList());
            persist(gen, SuggestionType.DISPATCH, SuggestionTargetType.TASK, task.id,
                    summary, payload, evidence);
            count++;
        }
        if (count == 0) {
            gen.notes.add("没有找到无主责的在办工单，未生成派工建议");
        }
    }

    /** 措施建议：在办工单有未关闭问题项时，召回同类型已完成工单的措施与耗时，并给出可建单草稿 */
    private void actionSuggestions(Gen gen) {
        int count = 0;
        for (Task task : gen.open) {
            if (count >= MAX_PER_TYPE) {
                break;
            }
            List<TaskIssue> issues = gen.issuesByTask.getOrDefault(task.id, List.of()).stream()
                    .filter(i -> i.status != null && !"CLOSED".equals(i.status.name())).toList();
            if (issues.isEmpty() || task.primaryAssigneeId == null || task.ownerId == null) {
                continue;
            }
            if (existsPending(SuggestionType.ACTION, task.id)) {
                gen.skipped++;
                continue;
            }
            List<Task> history = gen.done.stream()
                    .filter(t -> t.type == task.type && t.id != null && t.actualEnd != null)
                    .sorted(Comparator.comparing((Task t) -> t.actualEnd).reversed())
                    .limit(20).toList();
            if (history.isEmpty()) {
                continue;
            }
            List<String> measures = new ArrayList<>();
            List<Long> historyIds = new ArrayList<>();
            double totalHours = 0;
            int hoursSample = 0;
            for (Task t : history) {
                historyIds.add(t.id);
                if (t.createdAt != null && t.actualEnd != null) {
                    long minutes = Duration.between(t.createdAt, t.actualEnd).toMinutes();
                    if (minutes > 0) {
                        totalHours += minutes / 60.0;
                        hoursSample++;
                    }
                }
                for (TaskAction action : gen.actionsByTask.getOrDefault(t.id, List.of())) {
                    if (action.content != null && !action.content.isBlank()
                            && measures.stream().noneMatch(m -> m.equals(action.content.trim()))) {
                        measures.add(action.content.trim());
                    }
                    if (measures.size() >= 3) {
                        break;
                    }
                }
            }
            double avgHours = hoursSample == 0 ? DEFAULT_HOURS : round(totalHours / hoursSample);
            LocalDateTime planEnd = nextPlanEnd(gen.now, Math.max(1, (long) Math.ceil(avgHours)));
            // R1 要求负责人与执行人分开；来源单两人相同时，复盘单改选部门内负载最低的人，避免建议不可执行
            Long assigneeId = task.primaryAssigneeId;
            String reassignName = null;
            if (Objects.equals(task.ownerId, assigneeId)) {
                assigneeId = pickAssignee(gen, task);
                Employee replacement = assigneeId == null ? null : gen.employees.get(assigneeId);
                if (replacement == null) {
                    gen.notes.add("工单 " + task.taskNo
                            + " 的负责人与执行人相同，部门内没有可改派的人，未生成措施建议");
                    continue;
                }
                reassignName = replacement.getName();
            }
            String deptName = gen.deptNames.get(task.deptId);
            String summary = "工单 " + task.taskNo + "（" + deptName + "）还有 " + issues.size()
                    + " 条问题未关闭；同类型已完成 " + history.size() + " 张单，历史平均耗时 "
                    + fmtHours(avgHours) + "。建议按历史措施另起一张复盘单跟进。";
            if (reassignName != null) {
                summary += "来源单负责人与执行人相同，复盘单建议由 " + reassignName + " 执行。";
            }

            Map<String, Object> create = new LinkedHashMap<>();
            create.put("title", truncate("复盘：" + firstLine(issues.get(0).content) + "（源自 " + task.taskNo + "）", 120));
            create.put("description", "由 AI 措施建议采纳生成。来源工单 " + task.taskNo + "：\n"
                    + issues.stream().map(i -> "- " + firstLine(i.content)).toList()
                    + "\n历史措施：" + (measures.isEmpty() ? "暂无历史措施记录，请补充" : String.join("；", measures)));
            create.put("type", task.type == null ? "OTHER" : task.type.name());
            create.put("deptId", task.deptId);
            create.put("ownerId", task.ownerId);
            create.put("primaryAssigneeId", assigneeId);
            create.put("priority", task.priority == null ? 2 : task.priority);
            create.put("planEnd", planEnd.format(MINUTE));
            create.put("issueList", issues.stream().limit(3).map(i -> truncate(firstLine(i.content), 500)).toList());
            List<Map<String, Object>> actionList = new ArrayList<>();
            for (String measure : measures) {
                Map<String, Object> action = new LinkedHashMap<>();
                action.put("content", measure);
                action.put("ownerId", assigneeId);
                action.put("dueDate", planEnd.toLocalDate().toString());
                actionList.add(action);
            }
            create.put("actionList", actionList);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("summary", summary);
            payload.put("sourceTaskId", task.id);
            payload.put("sourceTaskNo", task.taskNo);
            payload.put("historyMeasures", measures);
            payload.put("historyAvgHours", avgHours);
            payload.put("create", create);

            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("taskId", task.id);
            evidence.put("taskNo", task.taskNo);
            evidence.put("issueIds", issues.stream().map(i -> i.id).toList());
            evidence.put("sameTypeDoneIds", historyIds);
            evidence.put("sameTypeDoneCount", history.size());
            evidence.put("avgHours", avgHours);
            persist(gen, SuggestionType.ACTION, SuggestionTargetType.TASK, task.id,
                    summary, payload, evidence);
            count++;
        }
    }

    /** 期限建议：已逾期或未定期限的在办工单，按同类已完成工单耗时的中位数与八成位给新期限 */
    private void dueDateSuggestions(Gen gen) {
        int count = 0;
        for (Task task : gen.open) {
            if (count >= MAX_PER_TYPE) {
                break;
            }
            boolean overdue = task.planEnd == null || task.planEnd.isBefore(gen.now);
            if (!overdue || task.status == TaskStatus.SUBMITTED) {
                continue;
            }
            if (existsPending(SuggestionType.DUE_DATE, task.id)) {
                gen.skipped++;
                continue;
            }
            List<Double> hours = durationHours(gen.done.stream()
                    .filter(t -> t.type == task.type && t.deptId != null && Objects.equals(t.deptId, task.deptId))
                    .toList());
            boolean sameType = !hours.isEmpty();
            if (hours.isEmpty()) {
                hours = durationHours(gen.done);
            }
            if (hours.isEmpty()) {
                hours = List.of((double) DEFAULT_HOURS);
            }
            List<Double> samples = new ArrayList<>(hours);
            samples.sort(Double::compareTo);
            double median = percentile(samples, 0.5);
            double p80 = percentile(samples, 0.8);
            long proposedHours = Math.max(1, (long) Math.ceil(median));
            LocalDateTime newPlanEnd = nextPlanEnd(gen.now, proposedHours);
            if (task.planEnd != null && newPlanEnd.equals(task.planEnd)) {
                newPlanEnd = newPlanEnd.plusHours(1);
            }
            int overdueDays = task.planEnd == null ? 0
                    : (int) Math.max(0, Duration.between(task.planEnd, gen.now).toDays());
            long overdueHours = task.planEnd == null ? 0
                    : Math.max(1, Duration.between(task.planEnd, gen.now).toHours());
            String overdueText = task.planEnd == null ? "还没有计划完成时间"
                    : (overdueDays > 0 ? "已逾期 " + overdueDays + " 天" : "已超过期限 " + overdueHours + " 小时");
            String summary = "工单 " + task.taskNo + "（" + gen.deptNames.get(task.deptId) + "）"
                    + overdueText
                    + "；同类工单耗时中位数 " + fmtHours(median) + "、八成位 " + fmtHours(p80)
                    + "，建议把期限改到 " + newPlanEnd.format(MINUTE) + "。";

            Map<String, Object> proposal = new LinkedHashMap<>();
            proposal.put("newPlanEnd", newPlanEnd.format(MINUTE));
            proposal.put("oldPlanEnd", task.planEnd == null ? null : task.planEnd.format(MINUTE));
            proposal.put("reason", "ESTIMATE");
            proposal.put("reasonText", "按同类工单历史耗时中位数 " + fmtHours(median) + " 与八成位 "
                    + fmtHours(p80) + " 重排期限，原计划未覆盖返工与等待时间。");
            proposal.put("rootCause", "同类工单在等待外部支持与返工环节耗时更长，原期限按理想工时估算，缺少缓冲。");
            proposal.put("measure", "按历史耗时重排期限，并把未关闭问题项拆给对应执行人跟进。");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("summary", summary);
            payload.put("proposal", proposal);
            payload.put("stats", Map.of("sample", hours.size(),
                    "medianHours", round(median), "p80Hours", round(p80),
                    "sameTypeSample", sameType));

            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("taskId", task.id);
            evidence.put("taskNo", task.taskNo);
            evidence.put("planEnd", task.planEnd == null ? null : task.planEnd.format(MINUTE));
            evidence.put("overdueDays", overdueDays);
            evidence.put("delayCount", task.delayValue());
            evidence.put("status", task.status.name());
            evidence.put("durationSamples", hours.size());
            persist(gen, SuggestionType.DUE_DATE, SuggestionTargetType.TASK, task.id,
                    summary, payload, evidence);
            count++;
        }
    }

    /** 升级建议：逾期、反复改期或多次催办时，按上级链给出升级对象 */
    private void escalateSuggestions(Gen gen) {
        int count = 0;
        for (Task task : gen.open) {
            if (count >= MAX_PER_TYPE) {
                break;
            }
            if (task.status == TaskStatus.SUBMITTED) {
                continue;
            }
            int overdueDays = task.planEnd == null ? 0
                    : (int) Math.max(0, Duration.between(task.planEnd, gen.now).toDays());
            boolean trigger = task.delayValue() >= 2 || task.urgeValue() >= 2 || overdueDays >= 7;
            if (!trigger) {
                continue;
            }
            if (existsPending(SuggestionType.ESCALATE, task.id)) {
                gen.skipped++;
                continue;
            }
            Employee owner = gen.employees.get(task.ownerId);
            Employee manager = owner == null || owner.getManagerId() == null
                    ? null : gen.employees.get(owner.getManagerId());
            if (manager == null) {
                Long leader = leaderOf(task.deptId);
                manager = leader == null ? null : gen.employees.get(leader);
            }
            if (manager == null) {
                manager = owner;
            }
            String managerName = manager == null ? "上级管理者" : manager.getName();
            String summary = "工单 " + task.taskNo + "（" + gen.deptNames.get(task.deptId) + "）"
                    + (overdueDays > 0 ? "已逾期 " + overdueDays + " 天，" : "")
                    + "改期 " + task.delayValue() + " 次、催办 " + task.urgeValue() + " 次，"
                    + "建议由 " + managerName + " 升级处理并给出资源或范围调整。";
            Map<String, Object> target = new LinkedHashMap<>();
            target.put("employeeId", manager == null ? null : manager.getId());
            target.put("name", managerName);
            target.put("deptId", manager == null ? null : manager.getDeptId());
            target.put("relation", owner != null && manager != null && Objects.equals(manager.getId(), owner.getManagerId())
                    ? "直属上级" : "部门负责人");
            target.put("note", "采纳升级建议，向 " + managerName + " 升级并催办该工单");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("summary", summary);
            payload.put("escalate", target);
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("taskId", task.id);
            evidence.put("taskNo", task.taskNo);
            evidence.put("ownerId", task.ownerId);
            evidence.put("delayCount", task.delayValue());
            evidence.put("urgeCount", task.urgeValue());
            evidence.put("overdueDays", overdueDays);
            evidence.put("planEnd", task.planEnd == null ? null : task.planEnd.format(MINUTE));
            persist(gen, SuggestionType.ESCALATE, SuggestionTargetType.TASK, task.id,
                    summary, payload, evidence);
            count++;
        }
    }

    /** 制度缺口建议：同类问题三十天内出现三次以上时提出 */
    private void policyGapSuggestions(Gen gen) {
        int gapTimes = Math.max(2, sysConfigRepository.getInt("ai.suggestion.gap.times", 3));
        List<Task> recent = taskRepository.findCreatedBetween(gen.now.minusDays(30), gen.now, null).stream()
                .filter(t -> inDept(gen.scope.deptIds(), t.deptId)).toList();
        List<Long> ids = recent.stream().map(t -> t.id).toList();
        Map<Long, List<TaskIssue>> issues = groupIssues(issueRepository.listByTaskIds(ids));
        Map<String, List<Task>> grouped = new LinkedHashMap<>();
        for (Task task : recent) {
            List<TaskIssue> rows = issues.getOrDefault(task.id, List.of());
            if (rows.isEmpty()) {
                continue;
            }
            String key = (task.deptId == null ? "-" : task.deptId) + "|"
                    + (task.type == null ? "OTHER" : task.type.name());
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(task);
        }
        int count = 0;
        for (Map.Entry<String, List<Task>> entry : grouped.entrySet()) {
            if (count >= MAX_PER_TYPE) {
                break;
            }
            if (entry.getValue().size() < gapTimes) {
                continue;
            }
            Task sample = entry.getValue().get(0);
            if (sample.deptId == null) {
                continue;
            }
            if (existsPending(SuggestionType.POLICY_GAP, sample.deptId)) {
                gen.skipped++;
                continue;
            }
            String typeLabel = sample.type == null ? "其他" : sample.type.label();
            String deptName = gen.deptNames.get(sample.deptId);
            List<Map<String, Object>> occurrences = new ArrayList<>();
            for (Task task : entry.getValue().stream().limit(5).toList()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("taskId", task.id);
                row.put("taskNo", task.taskNo);
                row.put("title", task.title);
                row.put("createdAt", task.createdAt == null ? null : task.createdAt.format(MINUTE));
                List<TaskIssue> rows = issues.getOrDefault(task.id, List.of());
                row.put("issue", rows.isEmpty() ? "" : truncate(firstLine(rows.get(0).content), 80));
                occurrences.add(row);
            }
            String summary = deptName + " 近 30 天出现 " + entry.getValue().size() + " 次「" + typeLabel
                    + "」类问题（阈值 " + gapTimes + " 次），建议补充对应的制度、检查表或知识文档，避免重复发生。";
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("summary", summary);
            payload.put("typeLabel", typeLabel);
            payload.put("occurrences", occurrences);
            payload.put("proposedAction", "在知识库补充该类问题的处理规范与检查表，并在下次评审中确认执行人");
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("deptId", sample.deptId);
            evidence.put("deptName", deptName);
            evidence.put("taskType", typeLabel);
            evidence.put("count", entry.getValue().size());
            evidence.put("taskIds", entry.getValue().stream().map(t -> t.id).toList());
            persist(gen, SuggestionType.POLICY_GAP, SuggestionTargetType.DEPT, sample.deptId,
                    summary, payload, evidence);
            count++;
        }
    }

    // ---------------------------------------------------------------- 查询

    public List<SuggestionView> list(AuthUser actor, String type, String targetType, String status, int limit) {
        SuggestionType parsedType = SuggestionType.parse(type);
        SuggestionTargetType parsedTarget = targetType == null || targetType.isBlank()
                ? null : SuggestionTargetType.parse(targetType);
        SuggestionStatus parsedStatus = parseStatus(status);
        TaskScope scope = DataScopeResolver.resolve(actor, deptRepository.findAll());
        Map<Long, String> employeeNames = new LinkedHashMap<>();
        for (Employee employee : employeeRepository.findAll()) {
            employeeNames.put(employee.getId(), employee.getName());
        }
        List<SuggestionView> views = new ArrayList<>();
        for (Suggestion suggestion : suggestionRepository.list(parsedType, parsedTarget, parsedStatus,
                limit <= 0 ? 50 : limit)) {
            if (!inScope(actor, scope, suggestion)) {
                continue;
            }
            views.add(toView(suggestion, employeeNames));
        }
        return views;
    }

    public SuggestionView detail(AuthUser actor, Long id) {
        Suggestion suggestion = require(id);
        TaskScope scope = DataScopeResolver.resolve(actor, deptRepository.findAll());
        if (!inScope(actor, scope, suggestion)) {
            throw new BizException(ErrorCode.OUT_OF_SCOPE, "该建议不在你的数据范围内");
        }
        return toView(suggestion, employeeNames());
    }

    // ---------------------------------------------------------------- 采纳

    @Transactional
    public SuggestionAcceptView accept(AuthUser actor, Long id) {
        requireAnyRole(actor);
        Suggestion suggestion = require(id);
        if (suggestion.status != SuggestionStatus.PENDING) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED,
                    "该建议已经是「" + suggestion.status.label() + "」，不能重复采纳");
        }
        TaskScope scope = DataScopeResolver.resolve(actor, deptRepository.findAll());
        if (!inScope(actor, scope, suggestion)) {
            throw new BizException(ErrorCode.OUT_OF_SCOPE, "该建议不在你的数据范围内");
        }
        Map<String, Object> payload = parseMap(suggestion.payload);
        LocalDateTime acceptedAt = LocalDateTime.now();
        String resultType;
        String objectType = null;
        Long objectId = null;
        String objectNo = null;
        String message;

        switch (suggestion.type) {
            case DISPATCH -> {
                Map<String, Object> dispatch = mapOf(payload.get("dispatch"));
                if (dispatch.isEmpty() || dispatch.get("primaryAssigneeId") == null || suggestion.targetId == null) {
                    throw new BizException(ErrorCode.RULE_UNSATISFIED, "建议里没有可执行的派工动作，请重新生成建议");
                }
                DispatchCommand command = new DispatchCommand(
                        toLong(dispatch.get("primaryAssigneeId")),
                        toLongList(dispatch.get("coAssigneeIds")),
                        null,
                        dispatch.get("priority") == null ? null : ((Number) dispatch.get("priority")).intValue(),
                        String.valueOf(dispatch.getOrDefault("note", "采纳派工建议")));
                TaskDetailView detail = taskAppService.dispatch(suggestion.targetId, command, actor);
                resultType = "DISPATCHED";
                objectType = "TASK";
                objectId = detail.task().id();
                objectNo = detail.task().taskNo();
                message = "已派工，工单 " + objectNo + " 仍为「" + detail.task().statusLabel() + "」";
            }
            case ACTION -> {
                Map<String, Object> create = mapOf(payload.get("create"));
                if (create.isEmpty()) {
                    throw new BizException(ErrorCode.RULE_UNSATISFIED, "建议里没有可执行的建单动作，请重新生成建议");
                }
                TaskOperations.CreateResult result = taskAppService.create(toCreateCommand(create), actor);
                resultType = "TASK_CREATED";
                objectType = "TASK";
                objectId = result.taskId();
                objectNo = result.taskNo();
                message = "已按措施建议建单 " + objectNo + "，来源工单 " + suggestion.targetId;
            }
            case DUE_DATE -> {
                Map<String, Object> proposal = mapOf(payload.get("proposal"));
                if (proposal.isEmpty() || proposal.get("newPlanEnd") == null || suggestion.targetId == null) {
                    throw new BizException(ErrorCode.RULE_UNSATISFIED, "建议里没有可执行的改期动作，请重新生成建议");
                }
                RescheduleCommand command = new RescheduleCommand(
                        parseDateTime(proposal.get("newPlanEnd")),
                        parseDateTime(proposal.get("oldPlanEnd")),
                        String.valueOf(proposal.getOrDefault("reason", "ESTIMATE")),
                        String.valueOf(proposal.getOrDefault("reasonText", "按同类工单历史耗时重排期限，原计划未覆盖返工与等待时间。")),
                        valueOf(proposal.get("rootCause")),
                        valueOf(proposal.get("measure")));
                TaskView view = taskAppService.reschedule(suggestion.targetId, command, actor);
                resultType = "RESCHEDULED";
                objectType = "TASK";
                objectId = view.id();
                objectNo = view.taskNo();
                message = "已改期，工单 " + objectNo + " 的计划完成时间为 "
                        + (view.planEnd() == null ? "空" : view.planEnd().format(MINUTE));
            }
            case ESCALATE -> {
                if (suggestion.targetId == null) {
                    throw new BizException(ErrorCode.RULE_UNSATISFIED, "建议里没有可执行的升级动作");
                }
                TaskOperations.UrgeResult result = taskAppService.urge(suggestion.targetId, actor);
                Task task = taskRepository.findById(suggestion.targetId);
                resultType = "ESCALATED";
                objectType = "TASK";
                objectId = suggestion.targetId;
                objectNo = task == null ? null : task.taskNo;
                message = "已升级并催办 " + result.urgeCount() + " 次：" + result.message();
            }
            case POLICY_GAP -> {
                resultType = "RECORDED";
                objectType = "DEPT";
                objectId = suggestion.targetId;
                message = "已记录制度缺口，请在知识库补充制度或检查表后关闭该建议";
            }
            default -> throw new BizException(ErrorCode.RULE_UNSATISFIED, "建议类型不支持采纳");
        }

        Map<String, Object> acceptResultMap = new LinkedHashMap<>();
        acceptResultMap.put("resultType", resultType);
        acceptResultMap.put("objectType", objectType == null ? "" : objectType);
        acceptResultMap.put("objectId", objectId == null ? 0L : objectId);
        acceptResultMap.put("objectNo", objectNo == null ? "" : objectNo);
        acceptResultMap.put("acceptedAt", acceptedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        String acceptResult = json(acceptResultMap);
        suggestionRepository.accept(id, actor.employeeId(), acceptedAt, acceptResult);
        audit(actor, "SUGGESTION_ACCEPT", id, Map.of("status", suggestion.status.name()), Map.of(
                "status", SuggestionStatus.ACCEPTED.name(),
                "type", suggestion.type.name(),
                "acceptedBy", String.valueOf(actor.employeeId()),
                "acceptedAt", acceptedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                "resultType", resultType,
                "objectType", objectType == null ? "" : objectType,
                "objectId", objectId == null ? "" : String.valueOf(objectId),
                "objectNo", objectNo == null ? "" : objectNo));
        return new SuggestionAcceptView(id, SuggestionStatus.ACCEPTED.name(), resultType, objectType,
                objectId, objectNo, message, acceptedAt);
    }

    // ---------------------------------------------------------------- 内部

    private void persist(Gen gen, SuggestionType type, SuggestionTargetType targetType, Long targetId,
                         String summary, Map<String, Object> payload, Map<String, Object> evidence) {
        Suggestion suggestion = new Suggestion();
        suggestion.type = type;
        suggestion.targetType = targetType;
        suggestion.targetId = targetId;
        suggestion.payload = json(payload);
        suggestion.evidence = json(evidence);
        suggestion.status = SuggestionStatus.PENDING;
        suggestion.modelName = MODEL_RULE;
        suggestion.promptVer = PROMPT_VER;
        suggestion.tokenIn = estimateTokens(summary + evidence);
        suggestion.tokenOut = estimateTokens(summary + payload);
        suggestion.latencyMs = (int) (System.currentTimeMillis() - gen.startedAt);
        suggestion.createdAt = LocalDateTime.now();
        suggestionRepository.insert(suggestion);
        gen.created++;
        gen.byType.merge(type.name(), 1, Integer::sum);
    }

    private SuggestionView toView(Suggestion suggestion, Map<Long, String> employeeNames) {
        SuggestionType type = suggestion.type;
        SuggestionTargetType targetType = suggestion.targetType == null
                ? SuggestionTargetType.TASK : suggestion.targetType;
        String targetName = targetName(targetType, suggestion.targetId);
        SuggestionStatus status = suggestion.status == null ? SuggestionStatus.PENDING : suggestion.status;
        String acceptedByName = suggestion.acceptedBy == null ? null : employeeNames.get(suggestion.acceptedBy);
        return new SuggestionView(suggestion.id, type.name(), type.label(), targetType.name(),
                targetType.label(), suggestion.targetId, targetName, parseJson(suggestion.payload),
                parseJson(suggestion.evidence), status.name(), status.label(), suggestion.modelName,
                suggestion.promptVer, suggestion.tokenIn, suggestion.tokenOut, suggestion.latencyMs,
                suggestion.acceptedBy, acceptedByName, suggestion.acceptedAt, suggestion.acceptResult,
                suggestion.createdAt);
    }

    private String targetName(SuggestionTargetType type, Long targetId) {
        if (targetId == null) {
            return null;
        }
        if (type == SuggestionTargetType.DEPT) {
            Dept dept = deptRepository.findById(targetId);
            return dept == null ? null : dept.getName();
        }
        if (type == SuggestionTargetType.EMPLOYEE) {
            Employee employee = employeeRepository.findById(targetId);
            return employee == null ? null : employee.getName();
        }
        Task task = taskRepository.findById(targetId);
        return task == null ? null : task.taskNo + " " + truncate(task.title, 40);
    }

    private boolean inScope(AuthUser actor, TaskScope scope, Suggestion suggestion) {
        if (scope.deptIds() == null && !scope.selfOnly()) {
            return true;
        }
        SuggestionTargetType type = suggestion.targetType == null ? SuggestionTargetType.TASK : suggestion.targetType;
        if (type == SuggestionTargetType.DEPT) {
            return inDept(scope.deptIds(), suggestion.targetId) && !scope.selfOnly();
        }
        if (type == SuggestionTargetType.EMPLOYEE) {
            Employee employee = suggestion.targetId == null ? null : employeeRepository.findById(suggestion.targetId);
            if (employee == null || employee.getDeptId() == null) {
                return false;
            }
            if (scope.selfOnly()) {
                return Objects.equals(actor.employeeId(), employee.getId());
            }
            return inDept(scope.deptIds(), employee.getDeptId());
        }
        Task task = suggestion.targetId == null ? null : taskRepository.findById(suggestion.targetId);
        if (task == null) {
            return false;
        }
        if (scope.selfOnly()) {
            return Objects.equals(actor.employeeId(), task.ownerId)
                    || Objects.equals(actor.employeeId(), task.primaryAssigneeId);
        }
        return inDept(scope.deptIds(), task.deptId);
    }

    private boolean inDept(List<Long> deptIds, Long deptId) {
        return deptIds == null || (deptId != null && deptIds.contains(deptId));
    }

    private boolean existsPending(SuggestionType type, Long targetId) {
        if (targetId == null) {
            return false;
        }
        SuggestionTargetType targetType = type == SuggestionType.POLICY_GAP
                ? SuggestionTargetType.DEPT : SuggestionTargetType.TASK;
        return suggestionRepository.existsPending(type, targetType, targetId);
    }

    /** 复盘单改派：部门树内、在职、非负责人本人，取在办负载最低的人 */
    private Long pickAssignee(Gen gen, Task task) {
        List<Long> deptIds = departmentTree(task.deptId);
        Map<Long, int[]> load = workload(gen, deptIds);
        Long best = null;
        int bestOpen = Integer.MAX_VALUE;
        for (Employee employee : gen.employees.values()) {
            if (employee.getDeptId() == null || !deptIds.contains(employee.getDeptId())) {
                continue;
            }
            if (Objects.equals(employee.getId(), task.ownerId)) {
                continue;
            }
            if (employee.getStatus() != null && employee.getStatus() != 1) {
                continue;
            }
            int open = load.getOrDefault(employee.getId(), new int[]{0, 0})[0];
            if (open < bestOpen) {
                bestOpen = open;
                best = employee.getId();
            }
        }
        return best;
    }

    private Map<Long, int[]> workload(Gen gen, List<Long> deptIds) {
        Map<Long, int[]> map = new LinkedHashMap<>();
        for (Task task : gen.open) {
            if (task.deptId == null || !deptIds.contains(task.deptId)) {
                continue;
            }
            if (task.primaryAssigneeId != null) {
                map.computeIfAbsent(task.primaryAssigneeId, k -> new int[]{0, 0})[0]++;
            }
            for (TaskAssignee assignee : gen.assignees) {
                if (assignee.taskId != null && Objects.equals(assignee.taskId, task.id)) {
                    map.computeIfAbsent(assignee.employeeId, k -> new int[]{0, 0})[0]++;
                }
            }
        }
        for (Task task : gen.done) {
            if (task.deptId == null || !deptIds.contains(task.deptId) || task.primaryAssigneeId == null) {
                continue;
            }
            map.computeIfAbsent(task.primaryAssigneeId, k -> new int[]{0, 0})[1]++;
        }
        return map;
    }

    private List<Long> departmentTree(Long deptId) {
        if (deptId == null) {
            return List.of();
        }
        List<Dept> depts = deptRepository.findAll();
        Dept mine = depts.stream().filter(d -> Objects.equals(d.getId(), deptId)).findFirst().orElse(null);
        if (mine == null || mine.getPath() == null) {
            return List.of(deptId);
        }
        return depts.stream()
                .filter(d -> d.getPath() != null
                        && io.tracelink.domain.org.rules.DeptTree.isSelfOrDescendant(mine.getPath(), d.getPath()))
                .map(Dept::getId).toList();
    }

    private Long leaderOf(Long deptId) {
        if (deptId == null) {
            return null;
        }
        Dept dept = deptRepository.findById(deptId);
        return dept == null ? null : dept.getLeaderEmployeeId();
    }

    private Map<Long, List<TaskIssue>> groupIssues(List<TaskIssue> rows) {
        Map<Long, List<TaskIssue>> map = new LinkedHashMap<>();
        for (TaskIssue issue : rows) {
            map.computeIfAbsent(issue.taskId, k -> new ArrayList<>()).add(issue);
        }
        return map;
    }

    private Map<Long, List<TaskAction>> groupActions(List<TaskAction> rows) {
        Map<Long, List<TaskAction>> map = new LinkedHashMap<>();
        for (TaskAction action : rows) {
            map.computeIfAbsent(action.taskId, k -> new ArrayList<>()).add(action);
        }
        return map;
    }

    private List<Double> durationHours(List<Task> tasks) {
        List<Double> hours = new ArrayList<>();
        for (Task task : tasks) {
            if (task.createdAt == null || task.actualEnd == null) {
                continue;
            }
            long minutes = Duration.between(task.createdAt, task.actualEnd).toMinutes();
            if (minutes > 0) {
                hours.add(minutes / 60.0);
            }
        }
        return hours;
    }

    private double percentile(List<Double> sorted, double ratio) {
        if (sorted.isEmpty()) {
            return DEFAULT_HOURS;
        }
        int index = (int) Math.round((sorted.size() - 1) * ratio);
        index = Math.max(0, Math.min(sorted.size() - 1, index));
        return sorted.get(index);
    }

    private LocalDateTime nextPlanEnd(LocalDateTime now, long hours) {
        return now.truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusHours(hours).plusHours(1);
    }

    private TaskCreateCommand toCreateCommand(Map<String, Object> create) {
        LocalDateTime planEnd = parseDateTime(create.get("planEnd"));
        if (planEnd == null || planEnd.isBefore(LocalDateTime.now())) {
            planEnd = nextPlanEnd(LocalDateTime.now(), DEFAULT_HOURS);
        }
        List<TaskCreateCommand.TaskActionInput> actions = new ArrayList<>();
        for (Object row : listOf(create.get("actionList"))) {
            Map<String, Object> action = mapOf(row);
            if (action.isEmpty()) {
                continue;
            }
            String content = valueOf(action.get("content"));
            if (content == null || content.isBlank()) {
                continue;
            }
            actions.add(new TaskCreateCommand.TaskActionInput(content, toLong(action.get("ownerId")),
                    parseDate(action.get("dueDate"))));
        }
        List<String> issues = new ArrayList<>();
        for (Object row : listOf(create.get("issueList"))) {
            if (row != null) {
                issues.add(String.valueOf(row));
            }
        }
        return new TaskCreateCommand(
                valueOf(create.get("title")),
                valueOf(create.get("description")),
                valueOf(create.get("type")),
                "MANUAL",
                toLong(create.get("deptId")),
                toLong(create.get("ownerId")),
                toLong(create.get("primaryAssigneeId")),
                List.of(),
                null,
                create.get("priority") == null ? 2 : ((Number) create.get("priority")).intValue(),
                null,
                planEnd,
                List.of(),
                issues,
                actions,
                null);
    }

    private Suggestion require(Long id) {
        Suggestion suggestion = suggestionRepository.findById(id);
        if (suggestion == null) {
            throw BizException.notFound("建议不存在或已删除");
        }
        return suggestion;
    }

    private void audit(AuthUser actor, String action, Long bizId, Map<String, String> before,
                       Map<String, String> after) {
        AuditLog log = new AuditLog();
        log.actorId = actor.employeeId();
        log.action = action;
        log.bizType = BIZ_SUGGESTION;
        log.bizId = bizId;
        log.before = before == null ? null : json(before);
        log.after = after == null ? null : json(after);
        auditLogRepository.insert(log);
    }

    private SuggestionStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return SuggestionStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BizException.invalid("建议状态只支持 PENDING、ACCEPTED、IGNORED");
        }
    }

    private Map<Long, String> employeeNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Employee employee : employeeRepository.findAll()) {
            names.put(employee.getId(), employee.getName());
        }
        return names;
    }

    private Object parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(raw, Object.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return Map.of("raw", raw);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(String raw) {
        Object value = parseJson(raw);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOf(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Object> listOf(Object value) {
        return value instanceof List<?> list ? (List<Object>) list : List.of();
    }

    private List<Long> toLongList(Object value) {
        List<Long> ids = new ArrayList<>();
        for (Object item : listOf(value)) {
            Long id = toLong(item);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String valueOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDateTime parseDateTime(Object value) {
        String text = valueOf(value);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), MINUTE);
        } catch (java.time.format.DateTimeParseException e) {
            try {
                return LocalDateTime.parse(text.trim());
            } catch (java.time.format.DateTimeParseException ignored) {
                return null;
            }
        }
    }

    private LocalDate parseDate(Object value) {
        String text = valueOf(value);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return "{}";
        }
    }

    private int estimateTokens(String text) {
        return text == null ? 0 : Math.max(1, text.length() / 2);
    }

    private String firstLine(String text) {
        if (text == null) {
            return "";
        }
        String line = text.trim().split("\\n")[0].trim();
        return line.isEmpty() ? text.trim() : line;
    }

    private String truncate(String text, int max) {
        String value = text == null ? "" : text.trim();
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private String fmtHours(double hours) {
        return hours >= 10 ? String.valueOf(Math.round(hours)) + " 小时"
                : String.format(java.util.Locale.ROOT, "%.1f 小时", hours);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void requireManager(AuthUser actor) {
        if (actor == null || actor.roles() == null
                || !(actor.roles().contains(ROLE_ADMIN) || actor.roles().contains(ROLE_MANAGER))) {
            throw new BizException(ErrorCode.NO_PERMISSION, "建议生成仅对管理者开放");
        }
    }

    private void requireAnyRole(AuthUser actor) {
        if (actor == null || actor.roles() == null
                || !(actor.roles().contains(ROLE_ADMIN) || actor.roles().contains(ROLE_MANAGER)
                || actor.roles().contains(ROLE_OWNER))) {
            throw new BizException(ErrorCode.NO_PERMISSION, "只有管理者与负责人可以采纳建议");
        }
    }

    /** 一次生成的只读上下文，避免把十几个参数一路往下传 */
    private static final class Gen {
        private AuthUser actor;
        private TaskScope scope;
        private LocalDateTime now;
        private long startedAt;
        private List<Task> open = List.of();
        private List<Task> done = List.of();
        private List<TaskAssignee> assignees = List.of();
        private Map<Long, Employee> employees = Map.of();
        private Map<Long, String> deptNames = Map.of();
        private Map<Long, List<TaskIssue>> issuesByTask = Map.of();
        private Map<Long, List<TaskAction>> actionsByTask = Map.of();
        private int created;
        private int skipped;
        private final Map<String, Integer> byType = new LinkedHashMap<>();
        private final List<String> notes = new ArrayList<>();
    }
}
