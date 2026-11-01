package io.tracelink.domain.importing.port;

import java.util.ArrayList;
import java.util.List;

/** 解析后的一本工作簿：多页，页内是结构化行与生产块指标 */
public class ParsedWorkbook {

    public List<ParsedSheet> sheets = new ArrayList<>();

    public int sheetTotal() {
        return sheets.size();
    }

    public int nonEmptySheets() {
        return (int) sheets.stream().filter(s -> !s.empty).count();
    }

    public int rowTotal() {
        return sheets.stream().mapToInt(s -> s.rows.size()).sum();
    }

    public int metricTotal() {
        return sheets.stream().mapToInt(s -> s.metrics.size()).sum();
    }
}
