package io.tracelink.domain.kb.model;

import java.util.List;

/** 知识检索的数据范围：all 为真不按部门收窄；否则只取部门列表内与全员可见的文档 */
public record KbScope(boolean all, List<Long> deptIds) {

    public static KbScope unrestricted() {
        return new KbScope(true, List.of());
    }
}
