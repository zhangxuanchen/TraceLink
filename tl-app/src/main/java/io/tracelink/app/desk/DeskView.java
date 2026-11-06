package io.tracelink.app.desk;

import java.util.List;

/** 我的工作台：今日待办、待我验收、退回待改与我的缺口，一次取全 */
public record DeskView(
        String date,
        List<TaskCard> open,
        List<TaskCard> pendingVerify,
        List<TaskCard> returned,
        List<MyGap> gaps) {
}
