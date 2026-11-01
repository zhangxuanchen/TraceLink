package io.tracelink.app.gate;

import io.tracelink.domain.gate.model.GateCell;
import io.tracelink.domain.gate.model.GateCode;
import io.tracelink.domain.gate.model.GateGapDaily;
import io.tracelink.domain.gate.model.GateRule;
import io.tracelink.domain.gate.model.GateState;
import io.tracelink.domain.gate.model.Notification;
import io.tracelink.domain.gate.model.TaskGateSnapshot;
import io.tracelink.domain.gate.repository.GateGapDailyRepository;
import io.tracelink.domain.gate.repository.GateSnapshotRepository;
import io.tracelink.domain.gate.repository.NotificationRepository;
import io.tracelink.domain.org.repository.EmployeeRepository;
import io.tracelink.domain.stat.model.TaskMetric;
import io.tracelink.domain.stat.repository.TaskMetricRepository;
import io.tracelink.domain.task.model.Task;
import io.tracelink.domain.task.model.TaskAction;
import io.tracelink.domain.task.model.TaskCheckin;
import io.tracelink.domain.task.model.TaskIssue;
import io.tracelink.domain.task.repository.SysConfigRepository;
import io.tracelink.domain.task.repository.TaskActionRepository;
import io.tracelink.domain.task.repository.TaskCheckinRepository;
import io.tracelink.domain.task.repository.TaskIssueRepository;
import io.tracelink.domain.task.repository.TaskRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 完成条件日快照与缺口提醒（DESIGN 4.10）。
 * 每天凌晨两点跑一次：判定全部在办工单，写 task_gate_snapshot 与 gate_gap_daily，
 * 只对新增缺口与滞留超阈值的缺口发提醒，快照保留一百八十天。
 */
@Service
public class GateSnapshotService {

    private static final int MAX_TASKS = 20000;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final TaskRepository taskRepository;
    private final TaskIssueRepository issueRepository;
    private final TaskActionRepository actionRepository;
    private final TaskCheckinRepository checkinRepository;
    private final TaskMetricRepository metricRepository;
    private final GateSnapshotRepository snapshotRepository;
    private final GateGapDailyRepository gapDailyRepository;
    private final NotificationRepository notificationRepository;
    private final EmployeeRepository employeeRepository;
    private final SysConfigRepository sysConfigRepository;
    private final GateEvaluator evaluator;

    public GateSnapshotService(TaskRepository taskRepository,
                               TaskIssueRepository issueRepository,
                               TaskActionRepository actionRepository,
                               TaskCheckinRepository checkinRepository,
                               TaskMetricRepository metricRepository,
                               GateSnapshotRepository snapshotRepository,
                               GateGapDailyRepository gapDailyRepository,
                               NotificationRepository notificationRepository,
                               EmployeeRepository employeeRepository,
                               SysConfigRepository sysConfigRepository,
                               GateEvaluator evaluator) {
        this.taskRepository = taskRepository;
        this.issueRepository = issueRepository;
        this.actionRepository = actionRepository;
        this.checkinRepository = checkinRepository;
        this.metricRepository = metricRepository;
        this.snapshotRepository = snapshotRepository;
        this.gapDailyRepository = gapDailyRepository;
        this.notificationRepository = notificationRepository;
        this.employeeRepository = employeeRepository;
        this.sysConfigRepository = sysConfigRepository;
        this.evaluator = evaluator;
    }

    /** 凌晨两点快照当日截面；失败不抛出，下一日重跑会覆盖 */
    @Scheduled(cron = "${tracelink.gate.cron:0 0 2 * * *}")
    public void nightly() {
        try {
            run(LocalDate.now());
        } catch (Exception ignored) {
            // 定时任务失败不打断应用，页面会把 stale 标出来
        }
    }

    @Transactional
    public SnapshotResult run(LocalDate date) {
        long started = System.currentTimeMillis();
        LocalDate snapDate = date == null ? LocalDate.now() : date;

        List<Task> tasks = taskRepository.findOpenAll(MAX_TASKS);
        List<Long> taskIds = tasks.stream().map(t -> t.id).toList();
        Map<Long, List<TaskIssue>> issues = group(issueRepository.listByTaskIds(taskIds), i -> i.taskId);
        Map<Long, List<TaskAction>> actions = group(actionRepository.listByTaskIds(taskIds), a -> a.taskId);
        Map<Long, List<TaskCheckin>> checkins = group(checkinRepository.listByTaskIds(taskIds), c -> c.taskId);
        Map<Long, List<TaskMetric>> metrics = group(metricRepository.findByTaskIds(taskIds), m -> m.taskId);
        Map<Long, String> names = employeeNames();

        Map<String, List<GateRule>> rulesByType = new HashMap<>();
        for (Task task : tasks) {
            rulesByType.computeIfAbsent(typeOf(task), evaluator::effectiveRules);
        }

        LocalDate prevDate = snapshotRepository.latestDateBefore(snapDate);
        int jumpedDays = prevDate == null ? 0 : (int) ChronoUnit.DAYS.between(prevDate, snapDate);
        Map<String, Integer> prevGaps = new HashMap<>();
        if (prevDate != null) {
            for (TaskGateSnapshot row : snapshotRepository.findByDate(prevDate)) {
                if (row.state == GateState.TODO) {
                    prevGaps.put(key(row.taskId, row.gateCode), row.gapValue());
                }
            }
        }

        snapshotRepository.deleteByDate(snapDate);
        List<TaskGateSnapshot> rows = new ArrayList<>();
        int todoCount = 0;
        for (Task task : tasks) {
            List<GateRule> rules = rulesByType.get(typeOf(task));
            List<GateCell> cells = evaluator.evaluate(task, rules,
                    issues.getOrDefault(task.id, List.of()),
                    actions.getOrDefault(task.id, List.of()),
                    checkins.getOrDefault(task.id, List.of()),
                    metrics.getOrDefault(task.id, List.of()),
                    liveGaps(task.id, prevGaps, jumpedDays, prevDate), names);
            for (GateCell cell : cells) {
                TaskGateSnapshot row = new TaskGateSnapshot();
                row.snapDate = snapDate;
                row.taskId = task.id;
                row.deptId = task.deptId;
                row.taskType = typeOf(task);
                row.gateCode = cell.code;
                row.state = cell.state;
                row.gapDays = cell.state == GateState.TODO ? cell.gapDays : 0;
                row.holderId = cell.holderId;
                rows.add(row);
                if (cell.state == GateState.TODO) {
                    todoCount++;
                }
            }
        }
        snapshotRepository.insertAll(rows);
        aggregate(snapDate, rows, tasks);

        int alerts = notifyGaps(snapDate, tasks, rows);
        int pruned = prune(snapDate);

        return new SnapshotResult(snapDate.format(DAY), tasks.size(), rows.size(), todoCount, alerts,
                pruned, System.currentTimeMillis() - started);
    }

    /**
     * 实时地图继承滞留天数的入口：某工单在今日之前最近一次快照里的缺口天数，
     * 加上距今天数；没有历史缺口时返回空 Map，判定会按首次出现记为一天。
     */
    public Map<GateCode, Integer> previousGaps(Long taskId, LocalDate today) {
        Map<GateCode, Integer> result = new HashMap<>();
        List<TaskGateSnapshot> rows = snapshotRepository.findLatestByTaskBefore(taskId, today);
        if (rows.isEmpty()) {
            return result;
        }
        LocalDate base = rows.get(0).snapDate;
        int jump = (int) ChronoUnit.DAYS.between(base, today);
        for (TaskGateSnapshot row : rows) {
            if (row.state == GateState.TODO) {
                result.put(row.gateCode, row.gapValue() + Math.max(0, jump));
            }
        }
        return result;
    }

    private Map<GateCode, Integer> liveGaps(Long taskId, Map<String, Integer> prevGaps, int jumpedDays,
                                            LocalDate prevDate) {
        Map<GateCode, Integer> result = new HashMap<>();
        if (prevDate == null) {
            return result;
        }
        int jump = Math.max(1, jumpedDays);
        for (GateCode code : GateCode.values()) {
            Integer gap = prevGaps.get(key(taskId, code));
            if (gap != null) {
                result.put(code, gap + jump);
            }
        }
        return result;
    }

    private void aggregate(LocalDate date, List<TaskGateSnapshot> rows, List<Task> tasks) {
        Map<String, int[]> byDeptGate = new LinkedHashMap<>();
        for (TaskGateSnapshot row : rows) {
            String key = row.deptId + "|" + row.gateCode.name();
            int[] values = byDeptGate.computeIfAbsent(key, k -> new int[4]);
            switch (row.state) {
                case DONE -> values[0]++;
                case TODO -> {
                    values[1]++;
                    values[3] = Math.max(values[3], row.gapValue());
                }
                case NA -> values[2]++;
            }
        }
        gapDailyRepository.deleteByDate(date);
        List<GateGapDaily> daily = new ArrayList<>();
        byDeptGate.forEach((key, values) -> {
            String[] parts = key.split("\\|");
            GateGapDaily row = new GateGapDaily();
            row.snapDate = date;
            row.deptId = Long.parseLong(parts[0]);
            row.gateCode = GateCode.valueOf(parts[1]);
            row.doneCount = values[0];
            row.todoCount = values[1];
            row.naCount = values[2];
            row.maxGapDays = values[3];
            daily.add(row);
        });
        gapDailyRepository.insertAll(daily);
    }

    private int notifyGaps(LocalDate date, List<Task> tasks, List<TaskGateSnapshot> rows) {
        int first = sysConfigRepository.getInt("alert.gap.first", 3);
        int second = sysConfigRepository.getInt("alert.gap.second", 7);
        Map<Long, Task> taskById = tasks.stream().collect(Collectors.toMap(t -> t.id, t -> t, (a, b) -> a));
        int sent = 0;
        for (TaskGateSnapshot row : rows) {
            if (row.state != GateState.TODO) {
                continue;
            }
            Task task = taskById.get(row.taskId);
            if (task == null) {
                continue;
            }
            String level = null;
            if (row.gapValue() >= second) {
                level = "SECOND";
            } else if (row.gapValue() >= first) {
                level = "FIRST";
            } else if (row.gapValue() == 1) {
                level = "NEW";
            }
            if (level == null) {
                continue;
            }
            LocalDate episodeStart = date.minusDays(Math.max(0, row.gapValue() - 1));
            List<Long> recipients = new ArrayList<>();
            if (row.holderId != null) {
                recipients.add(row.holderId);
            }
            if ("SECOND".equals(level) && task.ownerId != null && !recipients.contains(task.ownerId)) {
                recipients.add(task.ownerId);
            }
            for (Long recipient : recipients) {
                if (notificationRepository.exists(task.id, row.gateCode, level, episodeStart, recipient)) {
                    continue;
                }
                Notification notification = new Notification();
                notification.recipientId = recipient;
                notification.type = "GAP";
                notification.taskId = task.id;
                notification.taskNo = task.taskNo;
                notification.gateCode = row.gateCode;
                notification.level = level;
                notification.gapDays = row.gapValue();
                notification.episodeStart = episodeStart;
                notification.title = "缺口提醒：" + row.gateCode.title() + " 已滞留 " + row.gapValue() + " 天";
                notification.content = contentOf(task, row, level, recipient, task.ownerId);
                notificationRepository.insert(notification);
                sent++;
            }
        }
        return sent;
    }

    private String contentOf(Task task, TaskGateSnapshot row, String level, Long recipient, Long ownerId) {
        String head = "工单 " + task.taskNo + "「" + task.title + "」的完成条件「" + row.gateCode.title() + "」";
        return switch (level) {
            case "NEW" -> head + "刚出现缺口，请承担者尽快补齐。";
            case "FIRST" -> head + "已滞留 " + row.gapValue() + " 天，请承担者补齐并回复进展。";
            default -> recipient != null && recipient.equals(ownerId)
                    ? head + "已滞留 " + row.gapValue() + " 天，承担者仍未补齐，请负责人跟进。"
                    : head + "已滞留 " + row.gapValue() + " 天，请承担者补齐，负责人已同步收到提醒。";
        };
    }

    private int prune(LocalDate date) {
        int retention = sysConfigRepository.getInt("gate.snapshot.retention.days", 180);
        LocalDate cutoff = date.minusDays(Math.max(1, retention));
        List<LocalDate> oldDates = snapshotRepository.findDatesBefore(cutoff);
        int pruned = 0;
        for (LocalDate old : oldDates) {
            if (gapDailyRepository.findByDate(old).isEmpty()) {
                List<TaskGateSnapshot> rows = snapshotRepository.findByDate(old);
                List<Task> tasks = new ArrayList<>();
                aggregate(old, rows, tasks);
            }
            snapshotRepository.deleteByDate(old);
            pruned++;
        }
        return pruned;
    }

    private Map<Long, String> employeeNames() {
        return employeeRepository.findAll().stream()
                .collect(Collectors.toMap(e -> e.getId(), e -> e.getName(), (a, b) -> a));
    }

    private String typeOf(Task task) {
        return task.type == null ? "OTHER" : task.type.name();
    }

    private static String key(Long taskId, GateCode code) {
        return taskId + "|" + code.name();
    }

    private static <T> Map<Long, List<T>> group(List<T> rows, java.util.function.Function<T, Long> keyFn) {
        Map<Long, List<T>> map = new LinkedHashMap<>();
        for (T row : rows) {
            map.computeIfAbsent(keyFn.apply(row), k -> new ArrayList<>()).add(row);
        }
        return map;
    }
}
