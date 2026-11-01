package io.tracelink.app.stats;

import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.org.model.Dept;
import io.tracelink.domain.org.model.Employee;
import io.tracelink.domain.org.repository.DeptRepository;
import io.tracelink.domain.org.repository.EmployeeRepository;
import io.tracelink.domain.stat.model.TaskMetric;
import io.tracelink.domain.stat.repository.TaskMetricRepository;
import io.tracelink.domain.task.model.Task;
import io.tracelink.domain.task.model.TaskStatus;
import io.tracelink.domain.task.repository.TaskCheckinRepository;
import io.tracelink.domain.task.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统计应用服务：指标口径只在 {@link #kpis} 里定义一次，驾驶舱、趋势、部门对比、人员分析都复用同一份。
 * 明细来自 task 与 task_metric，预聚合表由 StatSnapshotService 定时重算，用于长区间查询。
 */
@Service
public class StatsAppService {

    private static final String ROLE_ADMIN = "SYS_ADMIN";
    private static final String ROLE_MANAGER = "MANAGER";
    private static final String ROLE_OWNER = "OWNER";
    private static final String SCOPE_ALL = "ALL";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter WEEK = DateTimeFormatter.ofPattern("yyyy-Www");

    private final TaskRepository taskRepository;
    private final TaskCheckinRepository checkinRepository;
    private final TaskMetricRepository metricRepository;
    private final EmployeeRepository employeeRepository;
    private final DeptRepository deptRepository;

    public StatsAppService(TaskRepository taskRepository,
                           TaskCheckinRepository checkinRepository,
                           TaskMetricRepository metricRepository,
                           EmployeeRepository employeeRepository,
                           DeptRepository deptRepository) {
        this.taskRepository = taskRepository;
        this.checkinRepository = checkinRepository;
        this.metricRepository = metricRepository;
        this.employeeRepository = employeeRepository;
        this.deptRepository = deptRepository;
    }

    // ---------------------------------------------------------------- 驾驶舱

    public StatsOverviewView overview(AuthUser actor, LocalDate from, LocalDate to, Long deptId) {
        requireManager(actor);
        return computeOverview(from, to, scopeDept(actor, deptId));
    }

    /** 被导出模块复用：角色校验由调用方负责，口径与页面完全一致 */
    public StatsOverviewView computeOverview(LocalDate from, LocalDate to, Long deptId) {
        LocalDate start = from == null ? toOrToday(to).minusDays(29) : from;
        LocalDate end = toOrToday(to);
        Snapshot snapshot = snapshot(start, end, deptId);

        List<KpiView> kpis = kpis(snapshot, start, end);
        List<TrendPoint> trend = trendPoints(snapshot, start, end);
        List<DeptStat> depts = deptStats(snapshot, start, end);
        List<Slice> status = statusSlices(snapshot);
        List<Slice> bins = overdueBins(snapshot, end);
        List<TaskRefView> top = overdueTop(snapshot, end, deptNames());
        List<Slice> recurrent = recurrentSlices(snapshot);
        List<Slice> types = typeSlices(snapshot);
        List<HeatCell> heatmap = heatmap(snapshot, end);

        return new StatsOverviewView(start.format(DAY), end.format(DAY), "v1", kpis, trend, depts,
                status, bins, top, recurrent, types, heatmap);
    }

    // ---------------------------------------------------------------- 趋势

    public StatsTrendView trend(AuthUser actor, LocalDate from, LocalDate to, Long deptId) {
        requireOwner(actor);
        LocalDate end = toOrToday(to);
        LocalDate start = from == null ? end.minusDays(29) : from;
        Long dept = scopeDept(actor, deptId);
        Snapshot snapshot = snapshot(start, end, dept);
        return new StatsTrendView(start.format(DAY), end.format(DAY),
                trendPoints(snapshot, start, end), weekPoints(snapshot, start, end));
    }

    // ---------------------------------------------------------------- 工序

    public StatsProcessView process(AuthUser actor, LocalDate from, LocalDate to) {
        requireOwner(actor);
        return computeProcess(from, to);
    }

    /** 被导出模块复用：工序达成率只看 task_metric，与角色无关 */
    public StatsProcessView computeProcess(LocalDate from, LocalDate to) {
        LocalDate end = toOrToday(to);
        LocalDate start = from == null ? end.minusDays(29) : from;
        List<TaskMetric> metrics = metricRepository.findProcess(start, end);

        Map<String, double[]> byProcess = new LinkedHashMap<>();
        Map<String, double[]> byDate = new LinkedHashMap<>();
        Map<String, ProcessRow> rows = new LinkedHashMap<>();
        for (TaskMetric metric : metrics) {
            if (metric.dimCode == null || metric.statDate == null) {
                continue;
            }
            boolean plan = "PLAN_QTY".equals(metric.metricCode);
            boolean actual = "ACT_QTY".equals(metric.metricCode);
            if (!plan && !actual) {
                continue;
            }
            double value = metric.value == null ? 0 : metric.value.doubleValue();
            String key = metric.statDate + "|" + metric.dimCode;
            ProcessRow row = rows.getOrDefault(key,
                    new ProcessRow(metric.statDate.format(DAY), null, metric.dimCode, 0, 0, 0));
            double planned = row.planned() + (plan ? value : 0);
            double done = row.actual() + (actual ? value : 0);
            rows.put(key, new ProcessRow(row.date(), row.workshop(), row.process(), planned, done, 0));
            double[] pair = byProcess.computeIfAbsent(metric.dimCode, k -> new double[2]);
            double[] day = byDate.computeIfAbsent(metric.statDate.format(DAY), k -> new double[2]);
            pair[plan ? 0 : 1] += value;
            day[plan ? 0 : 1] += value;
        }
        List<ProcessRow> detail = new ArrayList<>();
        for (ProcessRow row : rows.values()) {
            detail.add(new ProcessRow(row.date(), row.workshop(), row.process(), row.planned(),
                    row.actual(), ratio(row.actual(), row.planned())));
        }
        detail.sort(Comparator.comparing(ProcessRow::date).thenComparing(ProcessRow::process));
        List<PairPoint> processPoints = new ArrayList<>();
        List<PairPoint> datePoints = new ArrayList<>();
        byProcess.forEach((k, v) -> processPoints.add(new PairPoint(k, v[0], v[1])));
        byDate.forEach((k, v) -> datePoints.add(new PairPoint(k, v[0], v[1])));
        datePoints.sort(Comparator.comparing(PairPoint::label));
        double plan = processPoints.stream().mapToDouble(PairPoint::planned).sum();
        double act = processPoints.stream().mapToDouble(PairPoint::actual).sum();
        return new StatsProcessView(start.format(DAY), end.format(DAY), detail, processPoints,
                datePoints, ratio(act, plan));
    }

    // ---------------------------------------------------------------- 人员

    public StatsPersonView person(AuthUser actor, LocalDate from, LocalDate to, Long deptId, Long employeeId) {
        requireManager(actor);
        return computePerson(from, to, scopeDept(actor, deptId), employeeId);
    }

    /** 被导出模块复用：人员分析口径与页面一致 */
    public StatsPersonView computePerson(LocalDate from, LocalDate to, Long deptId, Long employeeId) {
        LocalDate end = toOrToday(to);
        LocalDate start = from == null ? end.minusDays(29) : from;
        Snapshot snapshot = snapshot(start, end, deptId);
        List<PersonStat> persons = personStats(snapshot, start, end);

        PersonStat focus = null;
        if (employeeId != null) {
            focus = persons.stream().filter(p -> p.employeeId() != null && p.employeeId().equals(employeeId))
                    .findFirst().orElse(null);
        }
        if (focus == null && !persons.isEmpty()) {
            focus = persons.get(0);
        }
        List<RadarDim> radar = radar(focus);
        return new StatsPersonView(start.format(DAY), end.format(DAY),
                focus == null ? null : focus.employeeId(),
                focus == null ? null : focus.name(),
                persons, radar, funnel(snapshot));
    }

    // ---------------------------------------------------------------- 指标口径

    private List<KpiView> kpis(Snapshot snapshot, LocalDate start, LocalDate end) {
        // 「期内应完成」按计划完成时间落在区间内取，不能用 end.minusDays(1) 起算，否则只统计最后一天
        List<Task> due = snapshot.tasks.stream()
                .filter(t -> t.planEnd != null && inRange(t.planEnd.toLocalDate(), start, end))
                .toList();
        int done = snapshot.done.size();
        int onTime = (int) snapshot.done.stream().filter(this::onTime).count();
        double avgHours = snapshot.done.stream()
                .filter(this::hasDuration)
                .mapToDouble(this::hours)
                .average().orElse(0);
        List<Task> open = snapshot.tasks.stream().filter(t -> t.status.isOpen()).toList();
        int overdue = (int) open.stream().filter(t -> overdue(t, end)).count();
        int firstPass = (int) snapshot.done.stream().filter(t -> t.rejectValue() == 0).count();
        int traced = (int) snapshot.done.stream().filter(t -> checkinRepository.existsByTask(t.id)).count();
        int created = snapshot.created.size();
        int recurrent = (int) snapshot.created.stream().filter(t -> t.recurrenceOf != null).count();

        return List.of(
                new KpiView("completion_rate", "完成率", ratio(done, due.size()), "%", "期内验收通过数 ÷ 期内应完成数"),
                new KpiView("on_time_rate", "按时完成率", ratio(onTime, done), "%", "验收通过且实际完成不晚于计划的单数 ÷ 验收通过数"),
                new KpiView("avg_hours", "平均完成时长", round(avgHours), "小时",
                        "验收时间与计划开始时间之差的小时均值（无计划开始时取创建时间）"),
                new KpiView("overdue_rate", "逾期率", ratio(overdue, open.size()), "%", "期末逾期单数 ÷ 期末未完成单数"),
                new KpiView("first_pass_rate", "一次通过率", ratio(firstPass, done), "%", "未被退回的单数 ÷ 验收通过数"),
                new KpiView("evidence_rate", "结果留痕率", ratio(traced, done), "%", "存在结果类追踪记录的单数 ÷ 全部单数"),
                new KpiView("recurrence_rate", "复发率", ratio(recurrent, created), "%", "带复发标记的新增单数 ÷ 新增单数"));
    }

    // ---------------------------------------------------------------- 快照

    /** 统计用人口：区间内新增 + 区间内完成 + 近 90 天全部（用于在办、逾期、状态分布） */
    private Snapshot snapshot(LocalDate from, LocalDate to, Long deptId) {
        LocalDateTime fromTime = from.atStartOfDay();
        LocalDateTime toTime = to.plusDays(1).atStartOfDay();
        List<Task> created = taskRepository.findCreatedBetween(fromTime, toTime, deptId);
        List<Task> done = taskRepository.findDoneBetween(fromTime, toTime, deptId);
        // 补录进来的历史工单创建时间不在区间，但计划完成落在区间，应算进「应完成」
        List<Task> planned = taskRepository.findPlannedBetween(fromTime, toTime, deptId);
        List<Task> recent = taskRepository.findForDedup(deptId, from.minusDays(90).atStartOfDay());
        Map<Long, Task> merged = new LinkedHashMap<>();
        for (Task task : recent) {
            merged.put(task.id, task);
        }
        for (Task task : created) {
            merged.put(task.id, task);
        }
        for (Task task : done) {
            merged.put(task.id, task);
        }
        for (Task task : planned) {
            merged.put(task.id, task);
        }
        return new Snapshot(created, done, new ArrayList<>(merged.values()));
    }

    private record Snapshot(List<Task> created, List<Task> done, List<Task> tasks) {
    }

    private List<TrendPoint> trendPoints(Snapshot snapshot, LocalDate from, LocalDate to) {
        Map<String, Integer> created = new LinkedHashMap<>();
        Map<String, Integer> done = new LinkedHashMap<>();
        for (Task task : snapshot.created) {
            if (task.createdAt != null) {
                created.merge(task.createdAt.toLocalDate().format(DAY), 1, Integer::sum);
            }
        }
        for (Task task : snapshot.done) {
            if (task.actualEnd != null) {
                done.merge(task.actualEnd.toLocalDate().format(DAY), 1, Integer::sum);
            }
        }
        List<TrendPoint> points = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            String key = day.format(DAY);
            LocalDateTime moment = day.plusDays(1).atStartOfDay();
            int open = (int) snapshot.tasks.stream().filter(t -> openOn(t, moment)).count();
            points.add(new TrendPoint(key, created.getOrDefault(key, 0), done.getOrDefault(key, 0), open));
        }
        return points;
    }

    private List<TrendPoint> weekPoints(Snapshot snapshot, LocalDate from, LocalDate to) {
        Map<String, Integer> created = new LinkedHashMap<>();
        Map<String, Integer> done = new LinkedHashMap<>();
        for (Task task : snapshot.created) {
            if (task.createdAt != null) {
                created.merge(weekKey(task.createdAt.toLocalDate()), 1, Integer::sum);
            }
        }
        for (Task task : snapshot.done) {
            if (task.actualEnd != null) {
                done.merge(weekKey(task.actualEnd.toLocalDate()), 1, Integer::sum);
            }
        }
        Map<String, Integer> open = new LinkedHashMap<>();
        for (Task task : snapshot.tasks) {
            if (task.planEnd != null && task.status.isOpen()) {
                open.merge(weekKey(task.planEnd.toLocalDate()), 1, Integer::sum);
            }
        }
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(created.keySet());
        keys.addAll(done.keySet());
        keys.addAll(open.keySet());
        List<TrendPoint> points = new ArrayList<>();
        keys.stream().sorted().forEach(key -> points.add(new TrendPoint(key,
                created.getOrDefault(key, 0), done.getOrDefault(key, 0), open.getOrDefault(key, 0))));
        return points;
    }

    private String weekKey(LocalDate date) {
        return date.getYear() + "-W" + String.format("%02d", date.get(WeekFields.ISO.weekOfWeekBasedYear()));
    }

    private List<DeptStat> deptStats(Snapshot snapshot, LocalDate from, LocalDate to) {
        Map<Long, String> names = deptNames();
        Map<Long, List<Task>> byDept = new LinkedHashMap<>();
        for (Task task : snapshot.tasks) {
            byDept.computeIfAbsent(task.deptId, k -> new ArrayList<>()).add(task);
        }
        List<DeptStat> stats = new ArrayList<>();
        byDept.forEach((deptId, tasks) -> {
            List<Task> created = tasks.stream()
                    .filter(t -> t.createdAt != null && inRange(t.createdAt.toLocalDate(), from, to)).toList();
            List<Task> done = tasks.stream()
                    .filter(t -> t.status == TaskStatus.DONE && t.actualEnd != null
                            && inRange(t.actualEnd.toLocalDate(), from, to)).toList();
            List<Task> due = tasks.stream()
                    .filter(t -> t.planEnd != null && inRange(t.planEnd.toLocalDate(), from, to)).toList();
            int open = (int) tasks.stream().filter(t -> t.status.isOpen()).count();
            int overdue = (int) tasks.stream().filter(t -> overdue(t, to)).count();
            int onTime = (int) done.stream().filter(this::onTime).count();
            stats.add(new DeptStat(deptId, deptId == null ? "未分配" : names.getOrDefault(deptId, "部门" + deptId),
                    created.size(), done.size(), overdue, open,
                    ratio(done.size(), due.size()), ratio(onTime, done.size())));
        });
        stats.sort(Comparator.comparing(DeptStat::completionRate).reversed());
        return stats;
    }

    private List<Slice> statusSlices(Snapshot snapshot) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Task task : snapshot.tasks) {
            if (task.status == null) {
                continue;
            }
            counts.merge(task.status.label(), 1, Integer::sum);
        }
        List<Slice> slices = new ArrayList<>();
        counts.forEach((k, v) -> slices.add(new Slice(k, v)));
        return slices;
    }

    private List<Slice> overdueBins(Snapshot snapshot, LocalDate end) {
        int[] bins = new int[5];
        for (Task task : snapshot.tasks) {
            if (!overdue(task, end)) {
                continue;
            }
            int days = overdueDays(task, end);
            if (days <= 3) {
                bins[0]++;
            } else if (days <= 7) {
                bins[1]++;
            } else if (days <= 14) {
                bins[2]++;
            } else if (days <= 30) {
                bins[3]++;
            } else {
                bins[4]++;
            }
        }
        return List.of(new Slice("1-3 天", bins[0]), new Slice("4-7 天", bins[1]),
                new Slice("8-14 天", bins[2]), new Slice("15-30 天", bins[3]),
                new Slice("30 天以上", bins[4]));
    }

    private List<TaskRefView> overdueTop(Snapshot snapshot, LocalDate end, Map<Long, String> deptNames) {
        Map<Long, String> names = employeeNames();
        return snapshot.tasks.stream()
                .filter(t -> overdue(t, end))
                .sorted(Comparator.comparingInt((Task t) -> overdueDays(t, end)).reversed())
                .limit(10)
                .map(t -> new TaskRefView(t.id, t.taskNo, t.title,
                        t.deptId == null ? null : deptNames.get(t.deptId),
                        t.primaryAssigneeId == null ? null : names.get(t.primaryAssigneeId),
                        t.status == null ? null : t.status.label(),
                        overdueDays(t, end),
                        t.planEnd == null ? null : t.planEnd.toLocalDate().format(DAY)))
                .toList();
    }

    private List<Slice> recurrentSlices(Snapshot snapshot) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Task task : snapshot.created) {
            if (task.recurrenceOf == null) {
                continue;
            }
            String key = task.title == null ? "未命名" : task.title;
            counts.merge(key, 1, Integer::sum);
        }
        List<Slice> slices = new ArrayList<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> slices.add(new Slice(e.getKey(), e.getValue())));
        return slices;
    }

    private List<Slice> typeSlices(Snapshot snapshot) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Task task : snapshot.created) {
            String key = task.type == null ? "未分类" : task.type.label();
            counts.merge(key, 1, Integer::sum);
        }
        List<Slice> slices = new ArrayList<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> slices.add(new Slice(e.getKey(), e.getValue())));
        return slices;
    }

    private List<HeatCell> heatmap(Snapshot snapshot, LocalDate end) {
        Map<Long, String> deptNames = deptNames();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Task task : snapshot.tasks) {
            if (!overdue(task, end) || task.planEnd == null) {
                continue;
            }
            String row = task.deptId == null ? "未分配" : deptNames.getOrDefault(task.deptId, "部门" + task.deptId);
            counts.merge(row + "|" + weekKey(task.planEnd.toLocalDate()), 1, Integer::sum);
        }
        List<HeatCell> cells = new ArrayList<>();
        counts.forEach((k, v) -> {
            int idx = k.indexOf('|');
            cells.add(new HeatCell(k.substring(0, idx), k.substring(idx + 1), v));
        });
        cells.sort(Comparator.comparing(HeatCell::column).thenComparing(HeatCell::row));
        return cells;
    }

    private List<PersonStat> personStats(Snapshot snapshot, LocalDate from, LocalDate to) {
        Map<Long, String> names = employeeNames();
        Map<Long, String> deptNames = deptNames();
        Map<Long, Employee> employees = employeeMap();
        Map<Long, List<Task>> byPerson = new LinkedHashMap<>();
        for (Task task : snapshot.tasks) {
            Long key = task.primaryAssigneeId == null ? task.ownerId : task.primaryAssigneeId;
            if (key == null) {
                continue;
            }
            byPerson.computeIfAbsent(key, k -> new ArrayList<>()).add(task);
        }
        List<PersonStat> stats = new ArrayList<>();
        byPerson.forEach((id, tasks) -> {
            List<Task> created = tasks.stream()
                    .filter(t -> t.createdAt != null && inRange(t.createdAt.toLocalDate(), from, to)).toList();
            List<Task> done = tasks.stream()
                    .filter(t -> t.status == TaskStatus.DONE && t.actualEnd != null
                            && inRange(t.actualEnd.toLocalDate(), from, to)).toList();
            // 回填的历史工单创建时间在导入当天，「应完成」改按计划完成时间落在区间里数
            List<Task> due = tasks.stream()
                    .filter(t -> t.planEnd != null && inRange(t.planEnd.toLocalDate(), from, to)).toList();
            Employee employee = employees.get(id);
            int onTime = (int) done.stream().filter(this::onTime).count();
            int traced = (int) done.stream().filter(t -> checkinRepository.existsByTask(t.id)).count();
            double avgHours = done.stream()
                    .filter(this::hasDuration)
                    .mapToDouble(this::hours)
                    .average().orElse(0);
            stats.add(new PersonStat(id, names.getOrDefault(id, "员工" + id),
                    employee == null || employee.getDeptId() == null ? null : deptNames.get(employee.getDeptId()),
                    created.size(), due.size(), done.size(),
                    (int) tasks.stream().filter(t -> t.status.isOpen()).count(),
                    (int) tasks.stream().filter(t -> overdue(t, to)).count(),
                    (int) done.stream().filter(t -> t.rejectValue() > 0).count(),
                    ratio(onTime, done.size()), ratio(traced, done.size()), round(avgHours)));
        });
        stats.sort(Comparator.comparingInt(PersonStat::done).reversed());
        return stats;
    }

    private List<RadarDim> radar(PersonStat focus) {
        if (focus == null) {
            return List.of();
        }
        double output = Math.min(100, focus.done() * 10.0);
        double accept = focus.done() == 0 ? 0
                : 100.0 * (focus.done() - focus.rejected()) / focus.done();
        // onTimeRate / evidenceRate 已经是百分数，不能再乘 100
        double timely = Math.min(100, focus.onTimeRate());
        double evidence = Math.min(100, focus.evidenceRate());
        double stable = focus.open() == 0 ? 100 : Math.max(0, 100 - focus.overdue() * 100.0 / focus.open());
        double load = Math.min(100, focus.due() * 10.0);
        return List.of(new RadarDim("产出", round(output)), new RadarDim("质量", round(accept)),
                new RadarDim("及时", round(timely)), new RadarDim("留痕", round(evidence)),
                new RadarDim("稳定", round(stable)), new RadarDim("承接", round(load)));
    }

    private List<Slice> funnel(Snapshot snapshot) {
        int all = snapshot.tasks.size();
        int dispatched = (int) snapshot.tasks.stream().filter(t -> t.primaryAssigneeId != null).count();
        int started = (int) snapshot.tasks.stream()
                .filter(t -> t.status != TaskStatus.PENDING && t.status != TaskStatus.DRAFT).count();
        int submitted = (int) snapshot.tasks.stream()
                .filter(t -> t.status == TaskStatus.SUBMITTED || t.status == TaskStatus.DONE).count();
        int done = (int) snapshot.tasks.stream().filter(t -> t.status == TaskStatus.DONE).count();
        return List.of(new Slice("创建", all), new Slice("已派工", dispatched),
                new Slice("已开工", started), new Slice("已提交", submitted), new Slice("已验收", done));
    }

    // ---------------------------------------------------------------- 工具

    private boolean openOn(Task task, LocalDateTime moment) {
        if (task.status == TaskStatus.CANCELLED) {
            return false;
        }
        LocalDateTime created = task.createdAt;
        LocalDateTime end = task.actualEnd;
        if (created != null && created.isAfter(moment)) {
            return false;
        }
        return end == null || !end.isBefore(moment);
    }

    private boolean overdue(Task task, LocalDate day) {
        LocalDateTime now = day.plusDays(1).atStartOfDay();
        return task.status != null && task.status.isOpen()
                && task.planEnd != null && task.planEnd.isBefore(now);
    }

    private int overdueDays(Task task, LocalDate day) {
        if (task.planEnd == null) {
            return 0;
        }
        long days = Duration.between(task.planEnd, day.plusDays(1).atStartOfDay()).toDays();
        return (int) Math.max(0, days);
    }

    private boolean onTime(Task task) {
        return task.actualEnd != null && task.planEnd != null && !task.actualEnd.isAfter(task.planEnd);
    }

    /** 完成时长的起点：计划开始优先。回填进来的历史工单 created_at 是导入当天，用它算时长会得出负数 */
    private LocalDateTime durationStart(Task task) {
        return task.planStart != null ? task.planStart : task.createdAt;
    }

    private boolean hasDuration(Task task) {
        return task.actualEnd != null && durationStart(task) != null;
    }

    private double hours(Task task) {
        LocalDateTime start = durationStart(task);
        if (start == null || task.actualEnd == null) {
            return 0;
        }
        return Math.max(0, Duration.between(start, task.actualEnd).toMinutes() / 60.0);
    }

    private boolean inRange(LocalDate day, LocalDate from, LocalDate to) {
        return !day.isBefore(from) && !day.isAfter(to);
    }

    private Map<Long, String> deptNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Dept dept : deptRepository.findAll()) {
            names.put(dept.getId(), dept.getName());
        }
        return names;
    }

    private Map<Long, String> employeeNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Employee employee : employeeRepository.findAll()) {
            names.put(employee.getId(), employee.getName());
        }
        return names;
    }

    private Map<Long, Employee> employeeMap() {
        Map<Long, Employee> map = new LinkedHashMap<>();
        for (Employee employee : employeeRepository.findAll()) {
            map.put(employee.getId(), employee);
        }
        return map;
    }

    private Long scopeDept(AuthUser actor, Long deptId) {
        if (actor == null) {
            return deptId;
        }
        if (SCOPE_ALL.equals(actor.dataScopeType())) {
            return deptId;
        }
        return actor.deptId() == null ? deptId : actor.deptId();
    }

    private void requireManager(AuthUser actor) {
        if (actor == null || !(actor.roles().contains(ROLE_ADMIN) || actor.roles().contains(ROLE_MANAGER))) {
            throw new BizException(ErrorCode.NO_PERMISSION, "驾驶舱与人员分析仅对管理者开放");
        }
    }

    private void requireOwner(AuthUser actor) {
        if (actor == null || !(actor.roles().contains(ROLE_ADMIN) || actor.roles().contains(ROLE_MANAGER)
                || actor.roles().contains(ROLE_OWNER))) {
            throw new BizException(ErrorCode.NO_PERMISSION, "统计查询仅对负责人及以上开放");
        }
    }

    private double ratio(double part, double total) {
        if (total <= 0) {
            return 0.0;
        }
        return round(part * 100.0 / total);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private LocalDate toOrToday(LocalDate to) {
        return to == null ? LocalDate.now() : to;
    }
}
