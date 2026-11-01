package io.tracelink.domain.exporting.model;

import java.util.ArrayList;
import java.util.List;

public class ExportWorkbook {

    public List<ExportSheet> sheets = new ArrayList<>();

    public ExportSheet add(String name) {
        ExportSheet sheet = new ExportSheet();
        sheet.name = name;
        sheets.add(sheet);
        return sheet;
    }
}
