package io.tracelink.app.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracelink.app.stats.PersonStat;
import io.tracelink.app.stats.StatsAppService;
import io.tracelink.app.stats.StatsPersonView;
import io.tracelink.common.api.PageResult;
import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.audit.model.AuditLog;
import io.tracelink.domain.audit.repository.AuditLogRepository;
import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.eval.model.Evaluation;
import io.tracelink.domain.eval.model.EvaluationStatus;
import io.tracelink.domain.eval.model.EvaluationTargetType;
import io.tracelink.domain.eval.repository.EvaluationRepository;
import io.tracelink.domain.org.model.Dept;
import io.tracelink.domain.org.model.Employee;
import io.tracelink.domain.org.repository.DeptRepository;
import io.tracelink.domain.org.repository.EmployeeRepository;
import io.tracelink.domain.org.rules.DataScopeResolver;
import io.tracelink.domain.task.model.Task;
import io.tracelink.domain.task.model.TaskIssue;
import io.tracelink.domain.task.model.TaskQuery;
import io.tracelink.domain.task.model.TaskScope;
import io.tracelink.domain.task.repository.SysConfigRepository;
import io.tracelink.domain.task.repository.TaskAssigneeRepository;
import io.tracelink.domain.task.repository.TaskIssueRepository;
import io.tracelink.domain.task.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 人员与任务评价（EVL-1/EVL-2）。人员口径直接复用 {@link StatsAppService#computePerson}，
 * 保证与 stats/person 同一区间同一个人数字一致，不允许两套算式（TC-M5-06）。
 * 每个结论都带证据 ID，权重读 sys_config，周期唯一，重复触发返回首次结果（Q4、TC-M5-05）。
 */
@Service
public class EvaluationAppService {

    private static final String ROLE_ADMIN = "SYS_ADMIN";
    private static final String ROLE_MANAGER = "MANAGER";
    private static final String BIZ_EVAL = "AI_EVAL";
    private static final Pattern MONTH = Pattern.compile("\\d{4}-\\d{2}");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MAX_TASK_EVAL = 50;
    private static final int MAX_TASK_SAMPLE = 50;

    private final EvaluationRepository evaluationRepository;
    private final StatsAppService statsAppService;
    private final TaskRepository taskRepository;
    private final TaskIssueRepository issueRepository;
    private final TaskAssigneeRepository assigneeRepository;
    private final EmployeeRepository employeeRepository;
    private final DeptRepository deptRepository;
    private final SysConfigRepository sysConfigRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public EvaluationAppService(EvaluationRepository evaluationRepository,
                                StatsAppService statsAppService,
                                TaskRepository taskRepository,
                                TaskIssueRepository issueRepository,
                                TaskAssigneeRepository assigneeRepository,
                                EmployeeRepository employeeRepository,
                                DeptRepository deptRepository,
                                SysConfigRepository sysConfigRepository,
                                AuditLogRepository auditLogRepository) {
        this.evaluationRepository = evaluationRepository;
        this.statsAppService = statsAppService;
        this.taskRepository = taskRepository;
        this.issueRepository = issueRepository;
        this.assigneeRepository = assigneeRepository;
        this.employeeRepository = employeeRepository;
        this.deptRepository = deptRepository;
        this.sysConfigRepository = sysConfigRepository;
        this.auditLogRepository = auditLogRepository;
    }

    // ---------------------------------------------------------------- 生成

    @Transactional
    public EvaluationRunView run(AuthUser actor, String period, String targetType) {
        requireManager(actor);
        YearMonth month = parsePeriod(period);
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        if (to.isAfter(LocalDate.now())) {
            to = LocalDate.now();
        }
        Long scopeDept = scopeDept(actor);
        TaskScope scope = DataScopeResolver.resolve(actor, deptRepository.findAll());
        List<String> notes = new ArrayList<>();
        int employees = 0;
        int tasks = 0;
        int skipped = 0;

        EvaluationTargetType type = parseTargetType(targetType);
        if (type == null || type == EvaluationTargetType.EMPLOYEE) {
            StatsPersonView persons = statsAppService.computePerson(from, to, scopeDept, null);
            for (PersonStat person : persons.persons()) {
                if (person.employeeId() == null) {
                    continue;
                }
                if (evaluationRepository.find(EvaluationTargetType.EMPLOYEE, person.employeeId(),
                        month.toString()) != null) {
                    skipped++;
                    continue;
                }
                evaluationRepository.insert(employeeEvaluation(person, month.toString(), scope));
                employees++;
            }
        }
        if (type == null || type == EvaluationTargetType.TASK) {
            List<Task> done = taskRepository.findDoneBetween(from.atStartOfDay(),
                    to.plusDays(1).atStartOfDay(), scopeDept).stream().limit(MAX_TASK_EVAL).toList();
            Map<Long, String> deptNames = deptNames();
            Map<Long, Employee> employeesById = employeesById();
            for (Task task : done) {
                if (evaluationRepository.find(EvaluationTargetType.TASK, task.id, month.toString()) != null) {
                    skipped++;
                    continue;
                }
                evaluationRepository.insert(taskEvaluation(task, month.toString(), deptNames, employeesById));
                tasks++;
            }
        }
        if (employees == 0 && tasks == 0 && skipped == 0) {
            notes.add("本周期内没有可评价的工单与人员，请确认周期与数据范围");
        }
        audit(actor, "EVAL_RUN", month.toString(), employees, tasks, skipped);
        return new EvaluationRunView(month.toString(), from.format(DAY), to.format(DAY),
                employees, tasks, skipped, notes);
    }

    private Evaluation employeeEvaluation(PersonStat person, String period, TaskScope scope) {
        int sample = person.due() + person.done() + person.open();
        int sampleTarget = Math.max(1, sysConfigRepository.getInt("eval.confidence.sample", 20));
        double confidence = Math.min(1.0, sample * 1.0 / sampleTarget);

        double completion = person.due() == 0 ? 0 : 100.0 * person.done() / person.due();
        double delivery = round(completion * 0.5 + person.onTimeRate() * 0.5);
        double evidence = round(person.evidenceRate());
        double response = person.open() == 0 ? 100 : Math.max(0, round(100 - 100.0 * person.overdue() / person.open()));
        double solution = person.done() == 0 ? 0 : Math.max(0, round(100 - 100.0 * person.rejected() / person.done()));

        List<Task> samples = taskRepository.page(new TaskQuery(null, null, null, null, null,
                person.employeeId(), false, false, false, LocalDateTime.now(), scope, 1, MAX_TASK_SAMPLE))
                .records();
        int coAssign = assigneeRepository.countByEmployee(person.employeeId());
        double cooperation = Math.min(100, coAssign * 20);
        int delayed = (int) samples.stream().filter(t -> t.delayValue() > 0).count();
        double compliance = person.done() == 0 ? 100
                : Math.max(0, round(100 - 100.0 * delayed / person.done()));

        double wDelivery = weight("eval.weight.delivery", 30);
        double wEvidence = weight("eval.weight.evidence", 20);
        double wResponse = weight("eval.weight.response", 15);
        double wSolution = weight("eval.weight.solution", 20);
        double wCoop = weight("eval.weight.cooperation", 10);
        double wCompliance = weight("eval.weight.compliance", 5);
        double totalWeight = wDelivery + wEvidence + wResponse + wSolution + wCoop + wCompliance;
        double overall = totalWeight == 0 ? 0
                : round((delivery * wDelivery + evidence * wEvidence + response * wResponse
                + solution * wSolution + cooperation * wCoop + compliance * wCompliance) / totalWeight);

        Evaluation evaluation = new Evaluation();
        evaluation.targetType = EvaluationTargetType.EMPLOYEE;
        evaluation.targetId = person.employeeId();
        evaluation.period = period;
        evaluation.score = decimal(overall);
        evaluation.confidence = decimal(confidence);
        evaluation.sampleCnt = sample;
        evaluation.status = EvaluationStatus.PUBLISHED;

        Map<String, Object> scores = new LinkedHashMap<>();
        scores.put("delivery", delivery);
        scores.put("evidence", evidence);
        scores.put("response", response);
        scores.put("solution", solution);
        scores.put("cooperation", cooperation);
        scores.put("compliance", compliance);
        scores.put("weights", Map.of("delivery", wDelivery, "evidence", wEvidence,
                "response", wResponse, "solution", wSolution,
                "cooperation", wCoop, "compliance", wCompliance));
        scores.put("note", "交付按完成率与按时率各半，留痕取结果记录率，响应取逾期占在办比，"
                + "解决取未退回比，协办按协办次数，规范按改期占完成比");
        evaluation.scores = json(scores);

        Map<String, Object> evidenceJson = new LinkedHashMap<>();
        evidenceJson.put("metrics", Map.of("created", person.created(), "due", person.due(),
                "done", person.done(), "open", person.open(), "overdue", person.overdue(),
                "rejected", person.rejected(), "onTimeRate", person.onTimeRate(),
                "evidenceRate", person.evidenceRate(), "avgHours", person.avgHours()));
        evidenceJson.put("taskIds", samples.stream().map(t -> t.id).toList());
        evidenceJson.put("coAssignCount", coAssign);
        evaluation.evidence = json(evidenceJson);
        return evaluation;
    }

    private Evaluation taskEvaluation(Task task, String period, Map<Long, String> deptNames,
                                      Map<Long, Employee> employeesById) {
        List<TaskIssue> issues = issueRepository.listByTask(task.id);
        List<io.tracelink.domain.task.model.TaskAssignee> assignees = assigneeRepository.listByTask(task.id);
        int assigneeDepts = (int) assignees.stream()
                .map(a -> {
                    Employee employee = employeesById.get(a.employeeId);
                    return employee == null ? null : employee.getDeptId();
                })
                .filter(Objects::nonNull)
                .distinct().count();
        double importance = 100 - (task.priority == null ? 2 : task.priority) * 25;
        double complexity = Math.min(100, issues.size() * 20 + assignees.size() * 10
                + (assigneeDepts > 1 ? 30 : 0));
        double health = Math.max(0, 100 - task.delayValue() * 15 - task.urgeValue() * 10);
        double quality = 100 - task.rejectValue() * 30 - (task.recurrenceOf == null ? 0 : 30);
        quality = Math.max(0, quality);
        double overall = round((importance + complexity + health + quality) / 4);

        Evaluation evaluation = new Evaluation();
        evaluation.targetType = EvaluationTargetType.TASK;
        evaluation.targetId = task.id;
        evaluation.period = period;
        evaluation.score = decimal(overall);
        evaluation.confidence = decimal(1.0);
        evaluation.sampleCnt = 1 + issues.size() + assignees.size();
        evaluation.status = EvaluationStatus.PUBLISHED;

        Map<String, Object> scores = new LinkedHashMap<>();
        scores.put("importance", importance);
        scores.put("complexity", complexity);
        scores.put("health", health);
        scores.put("quality", quality);
        scores.put("note", "重要度按优先级，复杂度按问题项与参与人，健康度按改期与催办，结果质量按退回与复发");
        evaluation.scores = json(scores);

        Map<String, Object> evidenceJson = new LinkedHashMap<>();
        evidenceJson.put("taskId", task.id);
        evidenceJson.put("taskNo", task.taskNo);
        evidenceJson.put("deptName", deptNames.get(task.deptId));
        evidenceJson.put("issueIds", issues.stream().map(i -> i.id).toList());
        evidenceJson.put("assigneeIds", assignees.stream().map(a -> a.employeeId).toList());
        evidenceJson.put("delayCount", task.delayValue());
        evidenceJson.put("urgeCount", task.urgeValue());
        evidenceJson.put("rejectCount", task.rejectValue());
        evaluation.evidence = json(evidenceJson);
        return evaluation;
    }

    // ---------------------------------------------------------------- 查询与申诉

    public List<EvaluationView> list(AuthUser actor, String period, String targetType, Long targetId,
                                     int limit) {
        requireManager(actor);
        EvaluationTargetType type = parseTargetType(targetType);
        Map<Long, String> employeeNames = new LinkedHashMap<>();
        for (Employee employee : employeeRepository.findAll()) {
            employeeNames.put(employee.getId(), employee.getName());
        }
        Map<Long, String> taskNos = new LinkedHashMap<>();
        List<Evaluation> rows = evaluationRepository.list(period, type, targetId, limit <= 0 ? 50 : limit);
        for (Evaluation evaluation : rows) {
            if (evaluation.targetType == EvaluationTargetType.TASK && !taskNos.containsKey(evaluation.targetId)) {
                Task task = taskRepository.findById(evaluation.targetId);
                taskNos.put(evaluation.targetId, task == null ? null : task.taskNo);
            }
        }
        return rows.stream().map(e -> toView(e, employeeNames, taskNos)).toList();
    }

    public EvaluationView detail(AuthUser actor, Long id) {
        requireManager(actor);
        Evaluation evaluation = evaluationRepository.findById(id);
        if (evaluation == null) {
            throw BizException.notFound("评价不存在");
        }
        Map<Long, String> employeeNames = new LinkedHashMap<>();
        for (Employee employee : employeeRepository.findAll()) {
            employeeNames.put(employee.getId(), employee.getName());
        }
        Map<Long, String> taskNos = new LinkedHashMap<>();
        if (evaluation.targetType == EvaluationTargetType.TASK) {
            Task task = taskRepository.findById(evaluation.targetId);
            taskNos.put(evaluation.targetId, task == null ? null : task.taskNo);
        }
        return toView(evaluation, employeeNames, taskNos);
    }

    @Transactional
    public EvaluationView appeal(AuthUser actor, Long id, String reason) {
        requireManager(actor);
        Evaluation evaluation = evaluationRepository.findById(id);
        if (evaluation == null) {
            throw BizException.notFound("评价不存在");
        }
        if (reason == null || reason.isBlank()) {
            throw BizException.invalid("请填写申诉理由");
        }
        String trimmed = reason.trim();
        Map<String, Object> evidence = new LinkedHashMap<>();
        Object parsed = parseJson(evaluation.evidence);
        if (parsed instanceof Map<?, ?> map) {
            map.forEach((key, value) -> evidence.put(String.valueOf(key), value));
        }
        evidence.put("appealReason", trimmed);
        evidence.put("appealedAt", LocalDateTime.now().format(DAY));
        evidence.put("appealedBy", actor.employeeId());
        evaluationRepository.updateAppeal(id, EvaluationStatus.APPEALED, json(evidence));
        AuditLog log = new AuditLog();
        log.actorId = actor.employeeId();
        log.action = "EVAL_APPEAL";
        log.bizType = BIZ_EVAL;
        log.bizId = id;
        log.after = json(Map.of("period", evaluation.period,
                "targetType", evaluation.targetType.name(),
                "targetId", evaluation.targetId,
                "reason", trimmed));
        auditLogRepository.insert(log);
        evaluation.status = EvaluationStatus.APPEALED;
        return detail(actor, id);
    }

    // ---------------------------------------------------------------- 工具

    private EvaluationView toView(Evaluation evaluation, Map<Long, String> employeeNames,
                                  Map<Long, String> taskNos) {
        EvaluationTargetType type = evaluation.targetType;
        String targetName = type == EvaluationTargetType.EMPLOYEE
                ? employeeNames.get(evaluation.targetId)
                : taskNos.get(evaluation.targetId);
        double score = evaluation.score == null ? 0 : evaluation.score.doubleValue();
        double confidence = evaluation.confidence == null ? 0 : evaluation.confidence.doubleValue();
        return new EvaluationView(evaluation.id, type.name(), type.label(), evaluation.targetId,
                targetName, evaluation.period, score, parseJson(evaluation.scores),
                parseJson(evaluation.evidence), confidence, evaluation.sampleCnt,
                evaluation.status.name(), evaluation.status.label(), evaluation.createdAt);
    }

    private void audit(AuthUser actor, String action, String period, int employees, int tasks, int skipped) {
        AuditLog log = new AuditLog();
        log.actorId = actor.employeeId();
        log.action = action;
        log.bizType = BIZ_EVAL;
        log.bizId = 0L;
        log.after = "{\"period\":\"" + period + "\",\"employeeCreated\":" + employees
                + ",\"taskCreated\":" + tasks + ",\"skipped\":" + skipped + "}";
        auditLogRepository.insert(log);
    }

    private YearMonth parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            return YearMonth.now();
        }
        if (!MONTH.matcher(period.trim()).matches()) {
            throw BizException.invalid("周期格式为 yyyy-MM，例如 2026-11");
        }
        return YearMonth.parse(period.trim());
    }

    private EvaluationTargetType parseTargetType(String targetType) {
        if (targetType == null || targetType.isBlank()) {
            return null;
        }
        try {
            return EvaluationTargetType.valueOf(targetType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BizException.invalid("评价对象只支持 EMPLOYEE 或 TASK");
        }
    }

    private Long scopeDept(AuthUser actor) {
        return "ALL".equals(actor.dataScopeType()) ? null : actor.deptId();
    }

    private double weight(String key, double defaultValue) {
        double value = sysConfigRepository.getDouble(key, defaultValue);
        return value <= 0 ? defaultValue : value;
    }

    private Map<Long, String> deptNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Dept dept : deptRepository.findAll()) {
            names.put(dept.getId(), dept.getName());
        }
        return names;
    }

    private Map<Long, Employee> employeesById() {
        Map<Long, Employee> map = new LinkedHashMap<>();
        for (Employee employee : employeeRepository.findAll()) {
            map.put(employee.getId(), employee);
        }
        return map;
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

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return "{}";
        }
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void requireManager(AuthUser actor) {
        if (actor == null || actor.roles() == null
                || !(actor.roles().contains(ROLE_ADMIN) || actor.roles().contains(ROLE_MANAGER))) {
            throw new BizException(ErrorCode.NO_PERMISSION, "人员与任务评价仅对管理者开放");
        }
    }
}
