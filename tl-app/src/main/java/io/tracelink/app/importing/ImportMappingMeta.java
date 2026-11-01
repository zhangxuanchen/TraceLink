package io.tracelink.app.importing;

import java.util.ArrayList;
import java.util.List;

/** 批次 mapping 列的 JSON 结构，预览页与确认页共用同一份，保证看到什么就入库什么 */
public class ImportMappingMeta {

    public String signature;
    public Integer sheetTotal;
    public List<Sheet> sheets = new ArrayList<>();
    public Recognition recognition;
    public List<Metric> metrics = new ArrayList<>();
    public String metricFrom;
    public String metricTo;

    public static class Sheet {
        public String name;
        public Integer headerRow;
        public String title;
        public String reporter;
        public String dateText;
        public String columns;
        public Integer rows;
        public Integer metrics;
        public Boolean empty;
    }

    public static class Recognition {
        public int detailRows;
        public int rowsWithAssignee;
        public int personMentions;
        public int personMatched;
        public double rowRate;
        public double personRate;
        public double rowTarget;
        public double personTarget;
        public boolean rowPass;
        public boolean personPass;
    }

    public static class Metric {
        public String statDate;
        public String workshop;
        public String process;
        public String planQty;
        public String actQty;
    }
}
