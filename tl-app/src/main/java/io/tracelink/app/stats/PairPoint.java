package io.tracelink.app.stats;

/** 计划与实际的成对值：工序达成率、计划完成对比图 */
public record PairPoint(String label, double planned, double actual) {

    public double rate() {
        return planned <= 0 ? 0.0 : actual / planned;
    }
}
