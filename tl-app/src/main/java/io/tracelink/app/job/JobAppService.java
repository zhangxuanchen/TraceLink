package io.tracelink.app.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracelink.common.api.PageResult;
import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.job.model.JobStatus;
import io.tracelink.domain.job.model.JobTask;
import io.tracelink.domain.job.model.JobType;
import io.tracelink.domain.job.repository.JobTaskRepository;
import io.tracelink.domain.org.model.Employee;
import io.tracelink.domain.org.repository.EmployeeRepository;
import io.tracelink.domain.task.repository.SysConfigRepository;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * 异步任务应用服务。请求先落一条 job_task，再由应用内线程池执行；
 * 页面拿任务 ID 轮询，关掉浏览器后重新打开还能在「最近任务」里看到结果。
 * 现有同步接口保持不变，只有显式走异步入口的操作才排队。
 */
@Service
public class JobAppService implements DisposableBean {

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String ROLE_ADMIN = "SYS_ADMIN";
    private static final String ROLE_MANAGER = "MANAGER";
    private static final int MAX_PAGE_SIZE = 100;

    private final JobTaskRepository jobTaskRepository;
    private final EmployeeRepository employeeRepository;
    private final SysConfigRepository sysConfigRepository;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "tl-job-executor");
        thread.setDaemon(true);
        return thread;
    });

    public JobAppService(JobTaskRepository jobTaskRepository,
                         EmployeeRepository employeeRepository,
                         SysConfigRepository sysConfigRepository,
                         ObjectMapper objectMapper) {
        this.jobTaskRepository = jobTaskRepository;
        this.employeeRepository = employeeRepository;
        this.sysConfigRepository = sysConfigRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }

    /** 每日清理已结束的旧任务，保留天数取 sys_config 的 job.retention.days（默认 30 天） */
    @Scheduled(cron = "${tracelink.job.cleanup-cron:0 20 1 * * *}")
    public void purgeExpired() {
        int retention = sysConfigRepository.getInt("job.retention.days", 30);
        jobTaskRepository.deleteBefore(LocalDateTime.now().minusDays(Math.max(1, retention)));
    }

    /** 同步操作也落一条任务记录，便于事后在任务列表里回溯 */
    public JobTask begin(JobType type, Map<String, Object> payload, AuthUser actor) {
        JobTask job = newTask(type, payload, actor, JobStatus.RUNNING, 10);
        jobTaskRepository.insert(job);
        return job;
    }

    public void progress(Long jobId, int percent) {
        JobTask job = jobTaskRepository.findById(jobId);
        if (job == null) {
            return;
        }
        job.progress = Math.max(0, Math.min(100, percent));
        jobTaskRepository.update(job);
    }

    public void complete(Long jobId, String resultText) {
        JobTask job = jobTaskRepository.findById(jobId);
        if (job == null) {
            return;
        }
        job.status = JobStatus.DONE;
        job.progress = 100;
        job.resultText = truncate(resultText, 500);
        job.lastError = null;
        job.updatedAt = LocalDateTime.now();
        jobTaskRepository.update(job);
    }

    public void fail(Long jobId, String errorText) {
        JobTask job = jobTaskRepository.findById(jobId);
        if (job == null) {
            return;
        }
        job.status = JobStatus.FAILED;
        job.attempts = (job.attempts == null ? 0 : job.attempts) + 1;
        job.lastError = truncate(errorText, 500);
        job.updatedAt = LocalDateTime.now();
        jobTaskRepository.update(job);
    }

    /** 排队执行：接口立即返回任务记录，调用方轮询 status */
    public JobView submitAsync(JobType type, Map<String, Object> payload, AuthUser actor,
                               String description, Supplier<String> work) {
        Map<String, Object> withDesc = new HashMap<>(payload == null ? Map.of() : payload);
        withDesc.put("desc", description);
        JobTask job = newTask(type, withDesc, actor, JobStatus.PENDING, 0);
        jobTaskRepository.insert(job);
        executor.execute(() -> run(job.id, work));
        return toView(job, nameOf(job.createdBy));
    }

    /** 同步执行包一层任务记录；异常先落 FAILED 再抛回，页面行为与原来一致 */
    public <T> T runRecorded(JobType type, Map<String, Object> payload, AuthUser actor,
                             String description, Supplier<T> work) {
        Map<String, Object> withDesc = new HashMap<>(payload == null ? Map.of() : payload);
        withDesc.put("desc", description);
        JobTask job = newTask(type, withDesc, actor, JobStatus.RUNNING, 20);
        jobTaskRepository.insert(job);
        try {
            T result = work.get();
            complete(job.id, describe(result));
            return result;
        } catch (RuntimeException e) {
            fail(job.id, e.getMessage());
            throw e;
        }
    }

    private void run(Long jobId, Supplier<String> work) {
        JobTask job = jobTaskRepository.findById(jobId);
        if (job == null) {
            return;
        }
        job.status = JobStatus.RUNNING;
        job.progress = 30;
        job.attempts = (job.attempts == null ? 0 : job.attempts) + 1;
        job.lockedBy = "app";
        jobTaskRepository.update(job);
        try {
            String result = work.get();
            complete(jobId, result);
        } catch (Throwable e) {
            fail(jobId, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    public PageResult<JobView> page(AuthUser actor, String jobType, String status, int pageNo, int pageSize) {
        JobType type = parseType(jobType);
        JobStatus state = parseStatus(status);
        Long creator = seesAll(actor) ? null : actor.employeeId();
        PageResult<JobTask> page = jobTaskRepository.page(creator, type, state,
                Math.max(1, pageNo), Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE));
        Map<Long, String> names = nameMap();
        List<JobView> records = page.records().stream()
                .map(job -> toView(job, names.get(job.createdBy)))
                .toList();
        return new PageResult<>(page.total(), page.pageNo(), page.pageSize(), records);
    }

    public JobView get(AuthUser actor, Long id) {
        JobTask job = jobTaskRepository.findById(id);
        if (job == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "任务不存在或已清理");
        }
        if (!seesAll(actor) && job.createdBy != null && !job.createdBy.equals(actor.employeeId())) {
            throw new BizException(ErrorCode.NO_PERMISSION, "只能查看自己发起的任务");
        }
        return toView(job, nameOf(job.createdBy));
    }

    private JobTask newTask(JobType type, Map<String, Object> payload, AuthUser actor,
                            JobStatus status, int progress) {
        JobTask job = new JobTask();
        job.jobType = type;
        job.payload = json(payload);
        job.status = status;
        job.progress = progress;
        job.attempts = 0;
        job.maxAttempts = 3;
        job.createdBy = actor == null ? null : actor.employeeId();
        return job;
    }

    private JobView toView(JobTask job, String createdByName) {
        String typeLabel = job.jobType == null ? "" : job.jobType.label();
        String statusLabel = job.status == null ? "" : job.status.label();
        return new JobView(
                job.id,
                job.jobType == null ? null : job.jobType.name(),
                typeLabel,
                job.status == null ? null : job.status.name(),
                statusLabel,
                job.payload,
                job.progress == null ? 0 : job.progress,
                job.attempts == null ? 0 : job.attempts,
                job.lastError,
                job.resultText,
                job.createdBy,
                createdByName,
                job.createdAt == null ? null : job.createdAt.format(MINUTE),
                job.updatedAt == null ? null : job.updatedAt.format(MINUTE));
    }

    private String json(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"payload 序列化失败\"}";
        }
    }

    private static String describe(Object result) {
        if (result == null) {
            return "done";
        }
        if (result instanceof CharSequence text) {
            return text.toString();
        }
        return result.toString();
    }

    private boolean seesAll(AuthUser actor) {
        return actor != null && (roleContains(actor, ROLE_ADMIN) || roleContains(actor, ROLE_MANAGER));
    }

    private static boolean roleContains(AuthUser actor, String role) {
        return actor.roles() != null && actor.roles().contains(role);
    }

    private JobType parseType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return JobType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BizException.invalid("未知任务类型：" + value);
        }
    }

    private JobStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return JobStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BizException.invalid("未知任务状态：" + value);
        }
    }

    private Map<Long, String> nameMap() {
        Map<Long, String> map = new HashMap<>();
        for (Employee employee : employeeRepository.findAll()) {
            map.put(employee.getId(), employee.getName());
        }
        return map;
    }

    private String nameOf(Long employeeId) {
        if (employeeId == null) {
            return null;
        }
        Employee employee = employeeRepository.findById(employeeId);
        return employee == null ? null : employee.getName();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
