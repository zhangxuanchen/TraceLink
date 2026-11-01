package io.tracelink.app.gate;

import io.tracelink.domain.gate.model.GateCell;
import io.tracelink.domain.gate.model.GateCode;
import io.tracelink.domain.gate.model.GateRule;
import io.tracelink.domain.gate.model.GateState;
import io.tracelink.domain.gate.model.HolderRole;
import io.tracelink.domain.gate.repository.GateRuleRepository;
import io.tracelink.domain.stat.model.TaskMetric;
import io.tracelink.domain.task.model.ActionStatus;
import io.tracelink.domain.task.model.CheckinResultType;
import io.tracelink.domain.task.model.ClosureLevel;
import io.tracelink.domain.task.model.IssueStatus;
import io.tracelink.domain.task.model.Task;
import io.tracelink.domain.task.model.TaskAction;
import io.tracelink.domain.task.model.TaskCheckin;
import io.tracelink.domain.task.model.TaskIssue;
import io.tracelink.domain.task.model.TaskStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 完成条件判定。九条条件全部从既有表派生，不新增业务字段（DESIGN 4.10）。
 * 口径读 gate_rule，同一类型下按 ALL 默认行再被类型行覆盖，权重与承担角色因此改配置即生效。
 */
@Component
public class GateEvaluator {

    private final GateRuleRepository ruleRepository;

    public GateEvaluator(GateRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    /** 取某工单类型的有效条件集合：先铺 ALL，再按类型覆盖，最后按 sort_no 排序 */
    public List<GateRule> effectiveRules(String taskType) {
        Map<GateCode, GateRule> merged = new EnumMap<>(GateCode.class);
        for (GateRule rule : ruleRepository.findAll()) {
            if (rule.gateCode == null) {
                continue;
            }
            if ("ALL".equalsIgnoreCase(rule.taskType)) {
                merged.put(rule.gateCode, rule);
            }
        }
        if (taskType != null) {
            for (GateRule rule : ruleRepository.findAll()) {
                if (rule.gateCode != null && taskType.equalsIgnoreCase(rule.taskType)) {
                    merged.put(rule.gateCode, rule);
                }
            }
        }
        List<GateRule> rules = new ArrayList<>(merged.values());
        rules.sort(Comparator.comparingInt(r -> r.sortNo == null ? 99 : r.sortNo));
        return rules;
    }

    /**
     * 判定一张工单的九条条件。
     *
     * @param prevGaps 上一次快照里仍未达成的条件及已滞留天数；实时地图由快照继承，首次出现传空
     */
    public List<GateCell> evaluate(Task task, List<GateRule> rules, List<TaskIssue> issues,
                                   List<TaskAction> actions, List<TaskCheckin> checkins,
                                   List<TaskMetric> metrics, Map<GateCode, Integer> prevGaps,
                                   Map<Long, String> employeeNames) {
        List<GateCell> cells = new ArrayList<>();
        for (GateRule rule : rules) {
            GateCell cell = GateCell.of(rule, GateState.NA, 0);
            if (!rule.enabledValue()) {
                cell.hint = "该类型不适用此条件，已从分母中排除";
                returnCell(cells, cell, task, employeeNames);
                continue;
            }
            if (task.status == TaskStatus.CANCELLED) {
                cell.state = GateState.NA;
                cell.hint = "工单已取消，完成地图不再追踪";
                returnCell(cells, cell, task, employeeNames);
                continue;
            }
            Judge judge = judge(rule, task, issues, actions, checkins, metrics);
            cell.state = judge.state;
            cell.hint = judge.hint;
            cell.gapDays = judge.state == GateState.TODO
                    ? prevGaps.getOrDefault(rule.gateCode, 1) : 0;
            cell.holderId = holderId(rule, task, actions);
            cell.holderName = employeeNames.get(cell.holderId);
            cell.actionUrl = rule.gateCode.actionUrl(task.id);
            returnCell(cells, cell, task, employeeNames);
        }
        return cells;
    }

    private void returnCell(List<GateCell> cells, GateCell cell, Task task, Map<Long, String> names) {
        if (cell.holderId == null) {
            cell.holderId = fallbackHolder(task);
        }
        if (cell.holderName == null) {
            cell.holderName = names.get(cell.holderId);
        }
        cells.add(cell);
    }

    public int completionRate(List<GateCell> cells) {
        int done = 0;
        int applicable = 0;
        for (GateCell cell : cells) {
            if (cell.state == GateState.NA) {
                continue;
            }
            applicable += cell.weight == null ? 0 : cell.weight;
            if (cell.state == GateState.DONE) {
                done += cell.weight == null ? 0 : cell.weight;
            }
        }
        return applicable == 0 ? 0 : (int) Math.round(done * 100.0 / applicable);
    }

    private Long holderId(GateRule rule, Task task, List<TaskAction> actions) {
        HolderRole role = rule.holderRole == null ? rule.gateCode.defaultHolder() : rule.holderRole;
        return switch (role) {
            case OWNER -> task.ownerId;
            case ACTION_OWNER -> actions.stream()
                    .filter(a -> a.status == ActionStatus.OPEN && a.ownerId != null)
                    .map(a -> a.ownerId)
                    .findFirst()
                    .orElseGet(() -> actions.stream()
                            .filter(a -> a.ownerId != null)
                            .map(a -> a.ownerId)
                            .findFirst()
                            .orElse(fallbackHolder(task)));
            case PRIMARY -> fallbackHolder(task);
        };
    }

    private Long fallbackHolder(Task task) {
        return task.primaryAssigneeId == null ? task.ownerId : task.primaryAssigneeId;
    }

    private record Judge(GateState state, String hint) {
        static Judge done(String hint) {
            return new Judge(GateState.DONE, hint);
        }

        static Judge todo(String hint) {
            return new Judge(GateState.TODO, hint);
        }

        static Judge na(String hint) {
            return new Judge(GateState.NA, hint);
        }
    }

    private Judge judge(GateRule rule, Task task, List<TaskIssue> issues, List<TaskAction> actions,
                        List<TaskCheckin> checkins, List<TaskMetric> metrics) {
        return switch (rule.gateCode) {
            case G1 -> task.status != TaskStatus.DRAFT && task.status != TaskStatus.PENDING
                    ? Judge.done("已接单，工单离开待接单状态")
                    : Judge.todo("主责尚未接单，先派工或写第一条追踪记录");
            case G2 -> judgeDeadline(task);
            case G3 -> judgeIssues(issues);
            case G4 -> judgeActions(actions);
            case G5 -> checkins.stream().anyMatch(c -> c.resultType == CheckinResultType.RESULT)
                    ? Judge.done("已有结果类追踪记录")
                    : Judge.todo("写一条结果类追踪记录，说明做完什么、结果如何");
            case G6 -> hasStrongEvidence(task, checkins)
                    ? Judge.done("已有强证据，验收或附件可核对")
                    : Judge.todo("在结果记录里留下可核对的证据，或让验收人确认");
            case G7 -> task.status == TaskStatus.BLOCKED
                    ? Judge.todo("阻碍尚未解除，解除后写一条恢复推进的记录")
                    : Judge.done("没有未解除的阻碍");
            case G8 -> judgeMetrics(metrics);
            case G9 -> judgeAcceptance(task, checkins);
        };
    }

    private Judge judgeDeadline(Task task) {
        if (task.planEnd == null) {
            return Judge.todo("补一个完成期限");
        }
        if (task.createdAt != null && task.planEnd.toLocalDate().isBefore(task.createdAt.toLocalDate())) {
            return Judge.todo("完成期限早于创建日，请负责人确认或改期");
        }
        return Judge.done("完成期限已确定");
    }

    private Judge judgeIssues(List<TaskIssue> issues) {
        if (issues.isEmpty()) {
            return Judge.na("该单没有登记问题项，不需要关闭");
        }
        long open = issues.stream().filter(i -> i.status == IssueStatus.OPEN).count();
        return open == 0
                ? Judge.done("问题项已全部关闭")
                : Judge.todo("还有 " + open + " 条问题未关闭，逐条关掉并记下处理结果");
    }

    private Judge judgeActions(List<TaskAction> actions) {
        if (actions.isEmpty()) {
            return Judge.na("该单没有登记措施项，不需要执行");
        }
        long noOwner = actions.stream().filter(a -> a.ownerId == null).count();
        if (noOwner > 0) {
            return Judge.todo("有 " + noOwner + " 条措施没有执行人，指定后通知到人");
        }
        long noDue = actions.stream().filter(a -> a.dueDate == null).count();
        if (noDue > 0) {
            return Judge.todo("有 " + noDue + " 条措施没有期限，补期限后才能排出优先级");
        }
        long open = actions.stream().filter(a -> a.status == ActionStatus.OPEN).count();
        return open == 0
                ? Judge.done("措施项已全部完成")
                : Judge.todo("有 " + open + " 条措施未完成，完成后逐条标记");
    }

    private boolean hasStrongEvidence(Task task, List<TaskCheckin> checkins) {
        return task.closureLevel == ClosureLevel.STRONG
                || checkins.stream().anyMatch(c -> c.closureLevel == ClosureLevel.STRONG);
    }

    private Judge judgeMetrics(List<TaskMetric> metrics) {
        if (metrics.isEmpty()) {
            return Judge.na("该单没有挂指标，不需要判定达成");
        }
        Map<String, BigDecimal> plan = new LinkedHashMap<>();
        Map<String, BigDecimal> actual = new LinkedHashMap<>();
        for (TaskMetric metric : metrics) {
            String key = (metric.statDate == null ? "-" : metric.statDate) + "|" + metric.dimCode;
            if ("PLAN_QTY".equals(metric.metricCode)) {
                plan.put(key, metric.value);
            } else if ("ACT_QTY".equals(metric.metricCode)) {
                actual.put(key, metric.value);
            }
        }
        if (plan.isEmpty()) {
            return Judge.na("指标没有目标值，只做记录不做达成判定");
        }
        for (Map.Entry<String, BigDecimal> entry : plan.entrySet()) {
            BigDecimal act = actual.get(entry.getKey());
            if (act == null || (entry.getValue() != null && act.compareTo(entry.getValue()) < 0)) {
                return Judge.todo("有指标实际值低于目标，补措施或更新目标");
            }
        }
        return Judge.done("指标实际值达到目标");
    }

    private Judge judgeAcceptance(Task task, List<TaskCheckin> checkins) {
        if (task.status == TaskStatus.DONE && task.resultSummary != null && !task.resultSummary.isBlank()) {
            return Judge.done("验收已通过，验收说明已填");
        }
        if (task.status == TaskStatus.SUBMITTED) {
            return Judge.todo("已提交验收，等待负责人通过");
        }
        if (task.rejectValue() > 0) {
            String reason = checkins.stream()
                    .filter(c -> c.resultType == CheckinResultType.REVIEW && c.content != null)
                    .map(c -> c.content)
                    .findFirst()
                    .orElse("");
            if (!reason.isBlank()) {
                String shortReason = reason.length() > 60 ? reason.substring(0, 60) + "…" : reason;
                return Judge.todo("验收被退回：" + shortReason);
            }
            return Judge.todo("验收被退回，按退回理由改完再提交");
        }
        return Judge.todo("完成并填写验收说明，再由负责人通过");
    }

    public boolean sameHolder(Long a, Long b) {
        return Objects.equals(a, b);
    }
}
