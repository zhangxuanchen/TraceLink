package io.tracelink.app.stats;

import io.tracelink.domain.org.repository.EmployeeRepository;
import io.tracelink.domain.stat.model.StatDaily;
import io.tracelink.domain.stat.model.StatDimType;
import io.tracelink.domain.stat.repository.StatDailyRepository;
import io.tracelink.domain.task.model.Task;
import io.tracelink.domain.task.repository.TaskRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计预聚合：每天凌晨把前一天按部门、人员落进 stat_daily，长区间趋势与导出读预聚合表，
 * 避免每次都扫明细（DESIGN 6.4）。重算可覆盖，指标编码不随页面改。
 */
@Service
public class StatSnapshotService {

    public static final String TASK_CREATED = "TASK_CREATED";
    public static final String TASK_DONE = "TASK_DONE";
    public static final String TASK_OPEN = "TASK_OPEN";
    public static final String TASK_OVERDUE = "TASK_OVERDUE";
    public static final String COMPLETION_RATE = "COMPLETION_RATE";
    public static final String ON_TIME_RATE = "ON_TIME_RATE";

    private final StatDailyRepository statDailyRepository;
    private final TaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;

    public StatSnapshotService(StatDailyRepository statDailyRepository,
                               TaskRepository taskRepository,
                               EmployeeRepository employeeRepository) {
        this.statDailyRepository = statDailyRepository;
        this.taskRepository = taskRepository;
        this.employeeRepository = employeeRepository;
    }

    /** 凌晨两点重算前一天；失败只记日志，不影响主流程 */
    @Scheduled(cron = "${tracelink.stat.cron:0 0 2 * * *}")
    public void nightly() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        try {
            rebuild(yesterday);
        } catch (Exception ignored) {
            // 定时任务失败不抛出，下一次重算会覆盖
        }
    }

    /** 重算某一天：先删后写，幂等 */
    public int rebuild(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        statDailyRepository.deleteByDate(date);

        List<Task> created = taskRepository.findCreatedBetween(start, end, null);
        List<Task> done = taskRepository.findDoneBetween(start, end, null);
        List<Task> recent = taskRepository.findForDedup(null, date.minusDays(90).atStartOfDay());

        Map<Long, int[]> byDept = new LinkedHashMap<>();
        Map<Long, int[]> byPerson = new LinkedHashMap<>();
        int[] total = new int[4];
        for (Task task : recent) {
            boolean open = task.status != null && task.status.isOpen();
            boolean overdue = open && task.planEnd != null && task.planEnd.isBefore(end);
            int[] dept = byDept.computeIfAbsent(task.deptId, k -> new int[4]);
            if (open) {
                dept[2]++;
                total[2]++;
                if (overdue) {
                    dept[3]++;
                    total[3]++;
                }
            }
        }
        for (Task task : created) {
            byDept.computeIfAbsent(task.deptId, k -> new int[4])[0]++;
            total[0]++;
            Long key = task.primaryAssigneeId == null ? task.ownerId : task.primaryAssigneeId;
            if (key != null) {
                byPerson.computeIfAbsent(key, k -> new int[4])[0]++;
            }
        }
        for (Task task : done) {
            byDept.computeIfAbsent(task.deptId, k -> new int[4])[1]++;
            total[1]++;
            Long key = task.primaryAssigneeId == null ? task.ownerId : task.primaryAssigneeId;
            if (key != null) {
                byPerson.computeIfAbsent(key, k -> new int[4])[1]++;
            }
        }

        List<StatDaily> rows = new ArrayList<>();
        byDept.forEach((deptId, values) -> {
            String code = deptId == null ? "0" : deptId.toString();
            rows.add(StatDaily.of(date, StatDimType.DEPT, code, TASK_CREATED, values[0]));
            rows.add(StatDaily.of(date, StatDimType.DEPT, code, TASK_DONE, values[1]));
            rows.add(StatDaily.of(date, StatDimType.DEPT, code, TASK_OPEN, values[2]));
            rows.add(StatDaily.of(date, StatDimType.DEPT, code, TASK_OVERDUE, values[3]));
            double rate = values[0] == 0 ? 0 : values[1] * 100.0 / (values[0] + values[2]);
            rows.add(StatDaily.of(date, StatDimType.DEPT, code, COMPLETION_RATE, rate));
        });
        byPerson.forEach((employeeId, values) -> {
            String code = employeeId.toString();
            rows.add(StatDaily.of(date, StatDimType.EMPLOYEE, code, TASK_CREATED, values[0]));
            rows.add(StatDaily.of(date, StatDimType.EMPLOYEE, code, TASK_DONE, values[1]));
        });
        rows.add(StatDaily.of(date, StatDimType.DEPT, "ALL", TASK_CREATED, total[0]));
        rows.add(StatDaily.of(date, StatDimType.DEPT, "ALL", TASK_DONE, total[1]));
        rows.add(StatDaily.of(date, StatDimType.DEPT, "ALL", TASK_OPEN, total[2]));
        rows.add(StatDaily.of(date, StatDimType.DEPT, "ALL", TASK_OVERDUE, total[3]));

        statDailyRepository.upsertAll(rows);
        return rows.size();
    }

    /** 补齐区间：用于页面切到长区间时按需重算 */
    public int backfill(LocalDate from, LocalDate to) {
        int total = 0;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            total += rebuild(day);
        }
        return total;
    }
}
