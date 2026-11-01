package io.tracelink.domain.importing.port;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 生产块的一格指标：某车间某工序某日的计划与完成数量 */
public class ParsedMetric {

    public LocalDate statDate;
    public String workshop;
    public String process;
    public BigDecimal planQty;
    public BigDecimal actQty;
}
