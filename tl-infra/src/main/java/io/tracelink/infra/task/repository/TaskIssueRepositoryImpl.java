package io.tracelink.infra.task.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.tracelink.domain.task.model.IssueStatus;
import io.tracelink.domain.task.model.TaskIssue;
import io.tracelink.domain.task.repository.TaskIssueRepository;
import io.tracelink.infra.task.entity.TaskIssueEntity;
import io.tracelink.infra.task.mapper.TaskIssueMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class TaskIssueRepositoryImpl implements TaskIssueRepository {

    private final TaskIssueMapper issueMapper;

    public TaskIssueRepositoryImpl(TaskIssueMapper issueMapper) {
        this.issueMapper = issueMapper;
    }

    @Override
    public void insert(TaskIssue issue) {
        TaskIssueEntity entity = toEntity(issue);
        entity.id = null;
        issueMapper.insert(entity);
        issue.id = entity.id;
    }

    @Override
    public List<TaskIssue> listByTask(Long taskId) {
        return issueMapper.selectList(new QueryWrapper<TaskIssueEntity>()
                        .eq("task_id", taskId)
                        .orderByAsc("seq")
                        .orderByAsc("id"))
                .stream().map(TaskIssueRepositoryImpl::toModel).toList();
    }

    @Override
    public TaskIssue findById(Long id) {
        TaskIssueEntity entity = issueMapper.selectById(id);
        return entity == null ? null : toModel(entity);
    }

    @Override
    public void close(Long id, LocalDateTime closedAt) {
        issueMapper.update(null, new UpdateWrapper<TaskIssueEntity>()
                .eq("id", id)
                .eq("status", IssueStatus.OPEN.name())
                .set("status", IssueStatus.CLOSED.name())
                .set("closed_at", closedAt));
    }

    @Override
    public int countOpenByTask(Long taskId) {
        return Math.toIntExact(issueMapper.selectCount(new QueryWrapper<TaskIssueEntity>()
                .eq("task_id", taskId)
                .eq("status", IssueStatus.OPEN.name())));
    }

    public static TaskIssue toModel(TaskIssueEntity e) {
        TaskIssue i = new TaskIssue();
        i.id = e.id;
        i.taskId = e.taskId;
        i.seq = e.seq;
        i.content = e.content;
        i.category = e.category;
        i.severity = e.severity;
        i.status = e.status;
        i.closedAt = e.closedAt;
        return i;
    }

    private static TaskIssueEntity toEntity(TaskIssue i) {
        TaskIssueEntity e = new TaskIssueEntity();
        e.id = i.id;
        e.taskId = i.taskId;
        e.seq = i.seq;
        e.content = i.content;
        e.category = i.category;
        e.severity = i.severity;
        e.status = i.status;
        e.closedAt = i.closedAt;
        return e;
    }
}
