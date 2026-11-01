package io.tracelink.app.stats;

import java.util.List;

/** 人员分析：散点行、选中人的雷达、整体完成漏斗 */
public record StatsPersonView(
        String from,
        String to,
        Long focusEmployeeId,
        String focusName,
        List<PersonStat> persons,
        List<RadarDim> radar,
        List<Slice> funnel) {
}
