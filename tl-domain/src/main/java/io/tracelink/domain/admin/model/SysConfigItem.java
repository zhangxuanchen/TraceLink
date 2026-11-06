package io.tracelink.domain.admin.model;

/** 参数配置行。valueType 决定页面用哪种控件与后端如何校验 */
public record SysConfigItem(
        Long id,
        String cfgKey,
        String cfgValue,
        String valueType,
        String groupCode,
        String remark,
        Long updatedBy,
        String updatedAt) {
}
