package io.tracelink.domain.importing.port;

import java.util.ArrayList;
import java.util.List;

/** 一页日报。标题行位置不固定，由解析器先定位表头再按列名取字段 */
public class ParsedSheet {

    public String name;
    public boolean empty;
    public String title;
    public String reporterText;
    public String positionText;
    public String dateText;
    public int headerRowNo;
    /** 列名与列号的映射签名，如 序号:0,工作内容:1,... */
    public String signature;
    public List<ParsedRow> rows = new ArrayList<>();
    public List<ParsedMetric> metrics = new ArrayList<>();
}
