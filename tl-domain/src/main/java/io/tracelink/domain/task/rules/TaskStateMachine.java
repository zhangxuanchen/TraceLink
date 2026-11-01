package io.tracelink.domain.task.rules;

import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.task.model.TaskStatus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 工单状态迁移与 R1–R8 规则的唯一落点。规则写在后端，不依赖前端约束（DESIGN 4.2）。
 * 应用服务只调用这里的断言，不自己判断状态是否可操作。
 */
public final class TaskStateMachine {

    private TaskStateMachine() {
    }

    public static void assertDispatchAllowed(TaskStatus status) {
        if (status == TaskStatus.DRAFT || status == TaskStatus.PENDING
                || status == TaskStatus.IN_PROGRESS || status == TaskStatus.BLOCKED) {
            return;
        }
        throw stateDenied("当前状态为「" + status.label() + "」，不能再改派主责与期限");
    }

    public static void assertCheckinAllowed(TaskStatus status) {
        if (status.isWorkable()) {
            return;
        }
        throw stateDenied("当前状态为「" + status.label() + "」，不能再写追踪记录");
    }

    public static void assertSubmitAllowed(TaskStatus status) {
        if (status == TaskStatus.IN_PROGRESS) {
            return;
        }
        if (status == TaskStatus.BLOCKED) {
            throw stateDenied("阻碍未解除，先解除阻碍或勾选「阻碍已解除」再提交验收");
        }
        throw stateDenied("当前状态为「" + status.label() + "」，不能提交验收");
    }

    public static void assertVerifyAllowed(TaskStatus status) {
        if (status == TaskStatus.SUBMITTED) {
            return;
        }
        throw stateDenied("当前状态为「" + status.label() + "」，不是待验收，不能验收");
    }

    public static void assertRescheduleAllowed(TaskStatus status) {
        if (status == TaskStatus.PENDING || status == TaskStatus.IN_PROGRESS || status == TaskStatus.BLOCKED) {
            return;
        }
        throw stateDenied("当前状态为「" + status.label() + "」，不能改期");
    }

    public static void assertUrgeAllowed(TaskStatus status) {
        if (status.isOpen() && status != TaskStatus.SUBMITTED) {
            return;
        }
        throw stateDenied("当前状态为「" + status.label() + "」，催办没有意义");
    }

    /** R1 主责唯一且不得与负责人同人 */
    public static void assertAssigneeDistinct(Long ownerId, Long assigneeId) {
        if (ownerId == null || assigneeId == null) {
            throw new BizException(ErrorCode.RULE_UNSATISFIED, "负责人与主责执行人都要指定（R1）");
        }
        if (ownerId.equals(assigneeId)) {
            throw new BizException(ErrorCode.RULE_UNSATISFIED, "主责执行人不能是负责人本人，验收与执行要分开（R1）");
        }
    }

    /** R2 期限非空且不得早于当前时间 */
    public static void assertPlanEnd(LocalDateTime planEnd, LocalDateTime now) {
        if (planEnd == null) {
            throw new BizException(ErrorCode.RULE_UNSATISFIED, "计划完成时间不能为空，无期限的工单不能开始计时（R2）");
        }
        if (planEnd.isBefore(now)) {
            throw new BizException(ErrorCode.RULE_UNSATISFIED, "计划完成时间不得早于当前时间，请改到今天或之后");
        }
    }

    /** R3 提交前至少一条结果类追踪记录 */
    public static void assertHasResultRecord(int resultCount) {
        if (resultCount <= 0) {
            throw new BizException(ErrorCode.RULE_UNSATISFIED,
                    "提交前至少要有一条结果类追踪记录，当前 0 条（R3）");
        }
    }

    /** R4 验收通过必须填写结果说明，退回必须写理由 */
    public static void assertPassInput(String resultSummary) {
        if (resultSummary == null || resultSummary.isBlank()) {
            throw new BizException(ErrorCode.RULE_UNSATISFIED, "验收通过要填写结果说明（R4）");
        }
    }

    public static void assertRejectInput(String rejectReason) {
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new BizException(ErrorCode.REJECT_WITHOUT_REASON, "退回必须选原因分类并写说明（R4）");
        }
        if (rejectReason.trim().length() < 10) {
            throw new BizException(ErrorCode.REJECT_WITHOUT_REASON, "退回理由不少于 10 字，写清哪里不达标");
        }
    }

    /** 逾期天数，当天过点为第 1 天 */
    public static int overdueDays(LocalDateTime planEnd, LocalDateTime now) {
        if (planEnd == null || now == null || !now.isAfter(planEnd)) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(planEnd.toLocalDate(), now.toLocalDate());
        return days <= 0 ? 1 : Math.toIntExact(days);
    }

    public static OverdueLevel overdueLevel(LocalDateTime planEnd, LocalDateTime now) {
        return OverdueLevel.of(overdueDays(planEnd, now));
    }

    /** R6 改期：新期限与原因必填，累计超过上限要求根因与新措施 */
    public static void assertReschedule(String reasonText, boolean toLater, boolean actorIsOwner,
                                        int delayCount, int delayLimit, String rootCause, String measure) {
        if (reasonText == null || reasonText.trim().length() < 20) {
            throw new BizException(ErrorCode.PARAM_INVALID, "改期说明不少于 20 字，写清为什么改");
        }
        if (toLater && !actorIsOwner) {
            throw new BizException(ErrorCode.RULE_UNSATISFIED, "改到更晚的日期需要负责人确认，先联系负责人确认再改");
        }
        if (delayCount + 1 > delayLimit) {
            if (rootCause == null || rootCause.trim().length() < 20) {
                throw new BizException(ErrorCode.RULE_UNSATISFIED,
                        "改期已累计 " + delayCount + " 次，超过 " + delayLimit + " 次要填写根因说明（不少于 20 字）（R6）");
            }
            if (measure == null || measure.trim().length() < 10) {
                throw new BizException(ErrorCode.RULE_UNSATISFIED,
                        "改期已累计 " + delayCount + " 次，超过 " + delayLimit + " 次要填写新的措施（不少于 10 字）（R6）");
            }
        }
    }

    private static BizException stateDenied(String message) {
        return new BizException(ErrorCode.STATE_NOT_ALLOWED, message);
    }
}
