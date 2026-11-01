package io.tracelink.domain.exporting.model;

import java.util.ArrayList;
import java.util.List;

/** 待写出的一个工作表。rows 里的 null 单元格自动跳过，保留列位 */
public class ExportSheet {

    public String name;
    public String title;
    public List<List<String>> prefixRows = new ArrayList<>();
    public List<String> headers = new ArrayList<>();
    public List<List<String>> rows = new ArrayList<>();
    /** 表尾附加块，如生产块：原样写出，保持可再导入 */
    public List<List<String>> footerRows = new ArrayList<>();
    public int[] columnWidths;
    public boolean boldHeader = true;
}
