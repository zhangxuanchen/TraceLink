package io.tracelink.infra.task.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.tracelink.common.api.PageResult;
import io.tracelink.domain.task.model.Task;
import io.tracelink.domain.task.model.TaskQuery;
import io.tracelink.domain.task.model.TaskScope;
import io.tracelink.domain.task.model.TaskStatus;
import io.tracelink.domain.task.repository.TaskRepository;
import io.tracelink.infra.task.entity.TaskEntity;
import io.tracelink.infra.task.mapper.TaskMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Repository
public class TaskRepositoryImpl implements TaskRepository {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final List<String> OPEN_STATUS = List.of(
            TaskStatus.PENDING.name(), TaskStatus.IN_PROGRESS.name(),
            TaskStatus.BLOCKED.name(), TaskStatus.SUBMITTED.name());

    private final TaskMapper taskMapper;

    public TaskRepositoryImpl(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Override
    public String nextTaskNo(LocalDate day) {
        String prefix = "T-" + DAY.format(day) + "-";
        QueryWrapper<TaskEntity> wrapper = new QueryWrapper<TaskEntity>()
                .likeRight("task_no", prefix)
                .orderByDesc("task_no")
                .last("LIMIT 1");
        List<TaskEntity> last = taskMapper.selectList(wrapper);
        int seq = 1;
        if (!last.isEmpty()) {
            String no = last.get(0).taskNo;
            seq = Integer.parseInt(no.substring(prefix.length())) + 1;
        }
        return prefix + String.format("%03d", seq);
    }

    @Override
    public void insert(Task task) {
        TaskEntity entity = toEntity(task);
        entity.id = null;
        if (entity.createdAt == null) {
            entity.createdAt = java.time.LocalDateTime.now();
        }
        taskMapper.insert(entity);
        task.id = entity.id;
        task.createdAt = entity.createdAt;
    }

    @Override
    public void update(Task task) {
        TaskEntity entity = toEntity(task);
        taskMapper.update(null, new UpdateWrapper<TaskEntity>()
                .eq("id", task.id)
                .set("title", entity.title)
                .set("description", entity.description)
                .set("type", entity.type == null ? null : entity.type.name())
                .set("source_type", entity.sourceType == null ? null : entity.sourceType.name())
                .set("source_ref", entity.sourceRef)
                .set("dept_id", entity.deptId)
                .set("owner_id", entity.ownerId)
                .set("primary_assignee_id", entity.primaryAssigneeId)
                .set("assignee_text", entity.assigneeText)
                .set("priority", entity.priority)
                .set("status", entity.status == null ? null : entity.status.name())
                .set("plan_start", entity.planStart)
                .set("plan_end", entity.planEnd)
                .set("actual_end", entity.actualEnd)
                .set("progress", entity.progress)
                .set("accept_criteria", entity.acceptCriteria)
                .set("result_summary", entity.resultSummary)
                .set("delay_count", entity.delayCount)
                .set("reject_count", entity.rejectCount)
                .set("urge_count", entity.urgeCount)
                .set("closure_level", entity.closureLevel == null ? null : entity.closureLevel.name())
                .set("recurrence_of", entity.recurrenceOf)
                .set("parent_id", entity.parentId)
                .set("tags", entity.tags)
                .set("deleted", entity.deleted == null || entity.deleted ? 1 : 0));
    }

    @Override
    public Task findById(Long id) {
        TaskEntity entity = taskMapper.selectById(id);
        if (entity == null || Boolean.TRUE.equals(entity.deleted)) {
            return null;
        }
        return toModel(entity);
    }

    @Override
    public PageResult<Task> page(TaskQuery query) {
        QueryWrapper<TaskEntity> wrapper = buildWrapper(query);
        wrapper.orderByAsc("plan_end").orderByAsc("priority").orderByDesc("id");
        Page<TaskEntity> page = taskMapper.selectPage(new Page<>(query.pageNo(), query.pageSize()), wrapper);
        List<Task> records = page.getRecords().stream().map(TaskRepositoryImpl::toModel).toList();
        return new PageResult<>(page.getTotal(), query.pageNo(), query.pageSize(), records);
    }

    @Override
    public long count(TaskQuery query) {
        return taskMapper.selectCount(buildWrapper(query));
    }

    @Override
    public List<Task> findOpenByDept(Long deptId) {
        return taskMapper.selectList(new QueryWrapper<TaskEntity>()
                        .eq("deleted", 0)
                        .eq("dept_id", deptId)
                        .in("status", OPEN_STATUS)
                        .orderByDesc("created_at")
                        .last("LIMIT 200"))
                .stream().map(TaskRepositoryImpl::toModel).toList();
    }

    @Override
    public List<Task> findDoneSince(Long deptId, LocalDateTime since) {
        return taskMapper.selectList(new QueryWrapper<TaskEntity>()
                        .eq("deleted", 0)
                        .eq("dept_id", deptId)
                        .eq("status", TaskStatus.DONE.name())
                        .ge("actual_end", since)
                        .orderByDesc("actual_end")
                        .last("LIMIT 200"))
                .stream().map(TaskRepositoryImpl::toModel).toList();
    }

    @Override
    public List<Task> findCreatedBetween(LocalDateTime from, LocalDateTime to, Long deptId) {
        QueryWrapper<TaskEntity> wrapper = new QueryWrapper<TaskEntity>()
                .eq("deleted", 0)
                .ge("created_at", from)
                .lt("created_at", to);
        if (deptId != null) {
            wrapper.eq("dept_id", deptId);
        }
        wrapper.orderByAsc("created_at");
        return taskMapper.selectList(wrapper).stream().map(TaskRepositoryImpl::toModel).toList();
    }

    @Override
    public List<Task> findPlannedBetween(LocalDateTime from, LocalDateTime to, Long deptId) {
        QueryWrapper<TaskEntity> wrapper = new QueryWrapper<TaskEntity>()
                .eq("deleted", 0)
                .ge("plan_end", from)
                .lt("plan_end", to);
        if (deptId != null) {
            wrapper.eq("dept_id", deptId);
        }
        wrapper.orderByAsc("plan_end");
        return taskMapper.selectList(wrapper).stream().map(TaskRepositoryImpl::toModel).toList();
    }

    @Override
    public List<Task> findDoneBetween(LocalDateTime from, LocalDateTime to, Long deptId) {
        QueryWrapper<TaskEntity> wrapper = new QueryWrapper<TaskEntity>()
                .eq("deleted", 0)
                .eq("status", TaskStatus.DONE.name())
                .ge("actual_end", from)
                .lt("actual_end", to);
        if (deptId != null) {
            wrapper.eq("dept_id", deptId);
        }
        wrapper.orderByAsc("actual_end");
        return taskMapper.selectList(wrapper).stream().map(TaskRepositoryImpl::toModel).toList();
    }

    @Override
    public List<Task> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return taskMapper.selectList(new QueryWrapper<TaskEntity>()
                        .eq("deleted", 0)
                        .in("id", ids))
                .stream().map(TaskRepositoryImpl::toModel).toList();
    }

    @Override
    public List<Task> findForDedup(Long deptId, LocalDateTime since) {
        QueryWrapper<TaskEntity> wrapper = new QueryWrapper<TaskEntity>()
                .eq("deleted", 0)
                .ge("created_at", since);
        if (deptId != null) {
            wrapper.eq("dept_id", deptId);
        }
        wrapper.last("LIMIT 5000");
        return taskMapper.selectList(wrapper).stream().map(TaskRepositoryImpl::toModel).toList();
    }

    @Override
    public List<Task> findOpenAll(int limit) {
        return taskMapper.selectList(new QueryWrapper<TaskEntity>()
                        .eq("deleted", 0)
                        .in("status", OPEN_STATUS)
                        .orderByAsc("dept_id")
                        .orderByAsc("id")
                        .last("LIMIT " + Math.max(1, limit)))
                .stream().map(TaskRepositoryImpl::toModel).toList();
    }

    @Override
    public List<Task> findChildren(Long parentId) {
        return taskMapper.selectList(new QueryWrapper<TaskEntity>()
                        .eq("deleted", 0)
                        .eq("parent_id", parentId)
                        .orderByAsc("created_at"))
                .stream().map(TaskRepositoryImpl::toModel).toList();
    }

    @Override
    public List<Task> findRecurrencesOf(Long taskId) {
        return taskMapper.selectList(new QueryWrapper<TaskEntity>()
                        .eq("deleted", 0)
                        .eq("recurrence_of", taskId)
                        .orderByAsc("created_at"))
                .stream().map(TaskRepositoryImpl::toModel).toList();
    }

    private QueryWrapper<TaskEntity> buildWrapper(TaskQuery query) {
        QueryWrapper<TaskEntity> wrapper = new QueryWrapper<TaskEntity>().eq("deleted", 0);
        if (query.keyword() != null && !query.keyword().isBlank()) {
            String kw = query.keyword().trim();
            wrapper.and(w -> w.like("title", kw).or().like("task_no", kw));
        }
        if (query.statuses() != null && !query.statuses().isEmpty()) {
            wrapper.in("status", query.statuses().stream().map(Enum::name).toList());
        }
        if (query.types() != null && !query.types().isEmpty()) {
            wrapper.in("type", query.types().stream().map(Enum::name).toList());
        }
        if (query.deptIds() != null && !query.deptIds().isEmpty()) {
            wrapper.in("dept_id", query.deptIds());
        }
        if (query.ownerId() != null) {
            wrapper.eq("owner_id", query.ownerId());
        }
        if (query.primaryAssigneeId() != null) {
            wrapper.eq("primary_assignee_id", query.primaryAssigneeId());
        }
        if (query.overdueOnly()) {
            wrapper.lt("plan_end", query.now() == null ? LocalDateTime.now() : query.now());
            wrapper.in("status", OPEN_STATUS);
        }
        if (query.blockedOnly()) {
            wrapper.eq("status", TaskStatus.BLOCKED.name());
        }
        TaskScope scope = query.scope();
        if (scope != null) {
            if (scope.deptIds() != null) {
                if (scope.deptIds().isEmpty()) {
                    wrapper.apply("1 = 0");
                } else {
                    wrapper.in("dept_id", scope.deptIds());
                }
            }
            if (scope.selfOnly()) {
                applySelfOnly(wrapper, scope.employeeId());
            }
        }
        if (query.mine()) {
            applySelfOnly(wrapper, query.scope() == null ? null : query.scope().employeeId());
        }
        return wrapper;
    }

    private void applySelfOnly(QueryWrapper<TaskEntity> wrapper, Long employeeId) {
        if (employeeId == null) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.apply("(owner_id = {0} OR primary_assignee_id = {0} OR EXISTS ("
                + "SELECT 1 FROM task_assignee ta WHERE ta.task_id = task.id AND ta.employee_id = {0}))", employeeId);
    }

    public static Task toModel(TaskEntity e) {
        Task t = new Task();
        t.id = e.id;
        t.taskNo = e.taskNo;
        t.title = e.title;
        t.description = e.description;
        t.type = e.type;
        t.sourceType = e.sourceType;
        t.sourceRef = e.sourceRef;
        t.deptId = e.deptId;
        t.ownerId = e.ownerId;
        t.primaryAssigneeId = e.primaryAssigneeId;
        t.assigneeText = e.assigneeText;
        t.priority = e.priority;
        t.status = e.status;
        t.planStart = e.planStart;
        t.planEnd = e.planEnd;
        t.actualEnd = e.actualEnd;
        t.progress = e.progress;
        t.acceptCriteria = e.acceptCriteria;
        t.resultSummary = e.resultSummary;
        t.delayCount = e.delayCount;
        t.rejectCount = e.rejectCount;
        t.urgeCount = e.urgeCount;
        t.closureLevel = e.closureLevel;
        t.recurrenceOf = e.recurrenceOf;
        t.parentId = e.parentId;
        t.tags = e.tags;
        t.deleted = Boolean.TRUE.equals(e.deleted);
        t.createdBy = e.createdBy;
        t.createdAt = e.createdAt;
        t.updatedAt = e.updatedAt;
        return t;
    }

    private static TaskEntity toEntity(Task t) {
        TaskEntity e = new TaskEntity();
        e.id = t.id;
        e.taskNo = t.taskNo;
        e.title = t.title;
        e.description = t.description;
        e.type = t.type;
        e.sourceType = t.sourceType;
        e.sourceRef = t.sourceRef;
        e.deptId = t.deptId;
        e.ownerId = t.ownerId;
        e.primaryAssigneeId = t.primaryAssigneeId;
        e.assigneeText = t.assigneeText;
        e.priority = t.priority;
        e.status = t.status;
        e.planStart = t.planStart;
        e.planEnd = t.planEnd;
        e.actualEnd = t.actualEnd;
        e.progress = t.progress;
        e.acceptCriteria = t.acceptCriteria;
        e.resultSummary = t.resultSummary;
        e.delayCount = t.delayCount;
        e.rejectCount = t.rejectCount;
        e.urgeCount = t.urgeCount;
        e.closureLevel = t.closureLevel;
        e.recurrenceOf = t.recurrenceOf;
        e.parentId = t.parentId;
        e.tags = t.tags;
        e.deleted = t.deleted;
        e.createdBy = t.createdBy;
        e.createdAt = t.createdAt;
        return e;
    }
}
