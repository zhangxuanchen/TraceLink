package io.tracelink.app.audit;

import io.tracelink.common.api.PageResult;
import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.audit.model.AuditLog;
import io.tracelink.domain.audit.repository.AuditLogQuery;
import io.tracelink.domain.audit.repository.AuditLogRepository;
import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.org.model.Employee;
import io.tracelink.domain.org.repository.EmployeeRepository;
import io.tracelink.domain.task.model.Task;
import io.tracelink.domain.task.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SYS-2 审计查询。审计只记写操作（R8），这里只读；
 * 工单类审计补上 taskNo 与标题，页面不必再逐个点开。
 */
@Service
public class AuditAppService {

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String ROLE_ADMIN = "SYS_ADMIN";
    private static final String ROLE_MANAGER = "MANAGER";
    private static final int MAX_PAGE_SIZE = 200;

    private final AuditLogRepository auditLogRepository;
    private final EmployeeRepository employeeRepository;
    private final TaskRepository taskRepository;

    public AuditAppService(AuditLogRepository auditLogRepository,
                           EmployeeRepository employeeRepository,
                           TaskRepository taskRepository) {
        this.auditLogRepository = auditLogRepository;
        this.employeeRepository = employeeRepository;
        this.taskRepository = taskRepository;
    }

    public PageResult<AuditLogView> page(AuthUser actor, String bizType, Long bizId, Long actorId,
                                         String action, LocalDate from, LocalDate to,
                                         int pageNo, int pageSize) {
        requireManager(actor);
        AuditLogQuery query = new AuditLogQuery(bizType, bizId, actorId, action, from, to,
                Math.max(1, pageNo), Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE));
        PageResult<AuditLog> page = auditLogRepository.page(query);
        Map<Long, String> names = actorNames();
        Map<Long, String> taskLabels = taskLabels(page.records());
        List<AuditLogView> records = page.records().stream()
                .map(log -> new AuditLogView(
                        log.id,
                        log.action,
                        log.bizType,
                        log.bizId,
                        labelOf(log.bizType, log.bizId, taskLabels),
                        log.actorId,
                        names.get(log.actorId),
                        log.before,
                        log.after,
                        log.traceId,
                        log.createdAt == null ? null : log.createdAt.format(MINUTE)))
                .toList();
        return new PageResult<>(page.total(), page.pageNo(), page.pageSize(), records);
    }

    private Map<Long, String> actorNames() {
        Map<Long, String> names = new HashMap<>();
        for (Employee employee : employeeRepository.findAll()) {
            names.put(employee.getId(), employee.getName());
        }
        return names;
    }

    private Map<Long, String> taskLabels(List<AuditLog> logs) {
        Set<Long> ids = new HashSet<>();
        for (AuditLog log : logs) {
            if ("TASK".equals(log.bizType) && log.bizId != null) {
                ids.add(log.bizId);
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> labels = new HashMap<>();
        for (Task task : taskRepository.findByIds(List.copyOf(ids))) {
            labels.put(task.id, task.taskNo + " " + task.title);
        }
        return labels;
    }

    private static String labelOf(String bizType, Long bizId, Map<Long, String> taskLabels) {
        if ("TASK".equals(bizType)) {
            return taskLabels.getOrDefault(bizId, "工单 #" + bizId);
        }
        return (bizType == null ? "对象" : bizType) + " #" + bizId;
    }

    private void requireManager(AuthUser actor) {
        if (actor == null || actor.roles() == null
                || !(actor.roles().contains(ROLE_ADMIN) || actor.roles().contains(ROLE_MANAGER))) {
            throw new BizException(ErrorCode.NO_PERMISSION, "审计日志仅管理者可查");
        }
    }
}
