package io.tracelink.app.stats;

import java.util.List;

/** 趋势：按日与按周两种粒度 */
public record StatsTrendView(String from, String to, List<TrendPoint> points, List<TrendPoint> weeks) {
}
