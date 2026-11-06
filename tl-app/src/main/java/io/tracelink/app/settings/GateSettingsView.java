package io.tracelink.app.settings;

import java.util.List;

/** 模板与参数页一次取全：有效条件分组、全部类型权重是否各自合计一百 */
public record GateSettingsView(
        String version,
        List<GateRuleGroupView> groups) {
}
