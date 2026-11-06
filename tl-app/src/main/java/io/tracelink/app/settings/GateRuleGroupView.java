package io.tracelink.app.settings;

import java.util.List;

/** 某一工单类型的有效完成条件集合；inherited 表示该行来自 ALL 默认口径 */
public record GateRuleGroupView(
        String taskType,
        String typeLabel,
        int totalWeight,
        boolean valid,
        List<GateRuleRowView> rows) {
}
