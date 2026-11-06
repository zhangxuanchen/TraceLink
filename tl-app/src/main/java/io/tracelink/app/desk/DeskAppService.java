package io.tracelink.app.desk;

import io.tracelink.app.gate.CompletionMapAppService;
import io.tracelink.app.gate.CompletionMapView;
import io.tracelink.app.gate.GateItem;
import io.tracelink.app.task.TaskAppService;
import io.tracelink.app.task.TaskView;
import io.tracelink.common.api.PageResult;
import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.task.model.TaskStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 我的工作台：负责人与执行者的首屏。
 * 待办按本人主责的在办单取，验收按本人负责的待验收单取，缺口复用单工单完成地图的实时算式。
 */
@Service
public class DeskAppService {

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_CARDS = 50;
    private static final int GAP_TASK_LIMIT = 15;

    private final TaskAppService taskAppService;
    private final CompletionMapAppService completionMapAppService;

    public DeskAppService(TaskAppService taskAppService,
                          CompletionMapAppService completionMapAppService) {
        this.taskAppService = taskAppService;
        this.completionMapAppService = completionMapAppService;
    }

    public DeskView desk(AuthUser actor) {
        List<TaskView> open = new ArrayList<>();
        for (TaskStatus status : List.of(TaskStatus.PENDING, TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED)) {
            open.addAll(tasks(actor, status, false));
        }
        open.sort(Comparator.comparing((TaskView view) -> view.planEnd() == null
                ? java.time.LocalDateTime.MAX : view.planEnd())
                .thenComparing(TaskView::id));

        List<TaskView> pendingVerify = tasks(actor, TaskStatus.SUBMITTED, true);
        List<TaskView> returned = open.stream()
                .filter(view -> "IN_PROGRESS".equals(view.status()) && view.rejectCount() != null
                        && view.rejectCount() > 0)
                .toList();

        List<MyGap> gaps = new ArrayList<>();
        for (TaskView view : open.stream().limit(GAP_TASK_LIMIT).toList()) {
            CompletionMapView map = completionMapAppService.map(view.id(), actor, false, null);
            for (GateItem gate : map.gates()) {
                if ("TODO".equals(gate.state())) {
                    gaps.add(new MyGap(view.id(), view.taskNo(), view.title(), gate.code(), gate.name(),
                            gate.gapDays(), gate.hint(), gate.actionUrl()));
                }
            }
        }
        gaps.sort(Comparator.comparingInt(MyGap::gapDays).reversed());

        return new DeskView(
                LocalDate.now().toString(),
                open.stream().limit(MAX_CARDS).map(DeskAppService::toCard).toList(),
                pendingVerify.stream().limit(MAX_CARDS).map(DeskAppService::toCard).toList(),
                returned.stream().limit(MAX_CARDS).map(DeskAppService::toCard).toList(),
                gaps.stream().limit(30).toList());
    }

    private List<TaskView> tasks(AuthUser actor, TaskStatus status, boolean asOwner) {
        PageResult<TaskView> page = taskAppService.page(actor, null, status.name(), null, null,
                asOwner ? actor.employeeId() : null,
                asOwner ? null : actor.employeeId(),
                false, false, false, 1, MAX_CARDS);
        return page.records();
    }

    private static TaskCard toCard(TaskView view) {
        return new TaskCard(
                view.id(),
                view.taskNo(),
                view.title(),
                view.status(),
                view.statusLabel(),
                view.planEnd() == null ? null : view.planEnd().format(MINUTE),
                view.overdue(),
                view.overdueDays(),
                view.progress() == null ? 0 : view.progress(),
                view.rejectCount() == null ? 0 : view.rejectCount(),
                view.primaryAssigneeName(),
                view.ownerName());
    }
}
