package io.tracelink.app.settings;

/** 参数配置行：分组与类型用于页面控件，remark 说明取值范围 */
public record ConfigView(
        Long id,
        String cfgKey,
        String cfgValue,
        String valueType,
        String groupCode,
        String remark,
        Long updatedBy,
        String updatedAt) {
}
