package io.tracelink.infra.importing.parser;

import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.importing.port.ParsedMetric;
import io.tracelink.domain.importing.port.ParsedRow;
import io.tracelink.domain.importing.port.ParsedSheet;
import io.tracelink.domain.importing.port.ParsedWorkbook;
import io.tracelink.domain.importing.port.WorkbookParser;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作日报工作簿解析器。表格的标题行、表头行位置各页不固定，按“先找表头、再按列名取值”
 * 的方式定位；生产块按“日期序列号行 + 计划/完成标签行 + 数值行”的三段结构识别。
 */
@Component
public class PoiWorkbookParser implements WorkbookParser {

    private static final String[] ERROR_TOKENS = {"#VALUE!", "#REF!", "#NAME?", "#DIV/0!", "#N/A", "#NULL!", "#NUM!"};
    private static final String[] REQUIRED_COLUMNS = {"content"};
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);
    private static final DateTimeFormatter DATE_TEXT = DateTimeFormatter.ofPattern("yyyy年M月d日");

    @Override
    public ParsedWorkbook parse(Path file) {
        ParsedWorkbook workbook = new ParsedWorkbook();
        try (OPCPackage pkg = OPCPackage.open(file.toFile(), PackageAccess.READ);
             XSSFWorkbook wb = new XSSFWorkbook(pkg)) {
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                workbook.sheets.add(readSheet(wb.getSheetAt(i), formatter, evaluator));
            }
        } catch (Exception e) {
            throw new BizException(ErrorCode.RULE_UNSATISFIED, "工作簿无法解析：" + e.getMessage());
        }
        return workbook;
    }

    private ParsedSheet readSheet(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        ParsedSheet parsed = new ParsedSheet();
        parsed.name = sheet.getSheetName();
        List<RowSnap> snaps = snapshot(sheet, formatter, evaluator);
        int headerIndex = findHeader(snaps);
        if (headerIndex < 0) {
            parsed.empty = true;
            return parsed;
        }
        readMeta(snaps, headerIndex, parsed);
        Map<String, Integer> columns = mapColumns(snaps.get(headerIndex));
        parsed.headerRowNo = snaps.get(headerIndex).rowNo + 1;
        parsed.signature = signature(columns);
        if (columns.size() < 2) {
            parsed.empty = true;
            return parsed;
        }
        List<RowSnap> body = snaps.subList(headerIndex + 1, snaps.size());
        readBody(body, columns, parsed);
        parsed.empty = parsed.rows.isEmpty() && parsed.metrics.isEmpty();
        return parsed;
    }

    private List<RowSnap> snapshot(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        List<RowSnap> snaps = new ArrayList<>();
        for (Row row : sheet) {
            RowSnap snap = new RowSnap(row.getRowNum());
            for (Cell cell : row) {
                if (cell == null) {
                    continue;
                }
                String text = clean(formatter.formatCellValue(cell, evaluator));
                if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                    double num = cell.getNumericCellValue();
                    LocalDate date = null;
                    if (DateUtil.isCellDateFormatted(cell)) {
                        LocalDateTime dt = cell.getLocalDateTimeCellValue();
                        date = dt == null ? null : dt.toLocalDate();
                    }
                    if (text == null) {
                        text = BigDecimal.valueOf(num).stripTrailingZeros().toPlainString();
                    }
                    snap.cells.put(cell.getColumnIndex(), new CellSnap(cell.getColumnIndex(), text, num, date));
                } else if (text != null) {
                    snap.cells.put(cell.getColumnIndex(), new CellSnap(cell.getColumnIndex(), text, null, null));
                }
            }
            snaps.add(snap);
        }
        // 去掉尾部整行为空的行，避免把页脚空白当数据
        while (!snaps.isEmpty() && snaps.get(snaps.size() - 1).isEmpty()) {
            snaps.remove(snaps.size() - 1);
        }
        return snaps;
    }

    private int findHeader(List<RowSnap> snaps) {
        for (int i = 0; i < snaps.size(); i++) {
            RowSnap snap = snaps.get(i);
            if (snap.textAt(0) != null && normalize(snap.textAt(0)).contains("序号")
                    && snap.containsText("工作内容")) {
                return i;
            }
            if (snap.containsText("工作内容") && snap.textCells().size() >= 3) {
                return i;
            }
        }
        return -1;
    }

    private void readMeta(List<RowSnap> snaps, int headerIndex, ParsedSheet sheet) {
        for (int i = 0; i < headerIndex; i++) {
            RowSnap snap = snaps.get(i);
            for (CellSnap cell : snap.cells.values()) {
                String text = cell.text;
                if (text == null) {
                    continue;
                }
                if (sheet.reporterText == null && text.contains("汇报人")) {
                    sheet.reporterText = afterLabel(text, "汇报人", snap, cell.col);
                } else if (sheet.positionText == null && text.contains("职务")) {
                    sheet.positionText = afterLabel(text, "职务", snap, cell.col);
                } else if (sheet.dateText == null && text.contains("日期")) {
                    sheet.dateText = afterLabel(text, "日期", snap, cell.col);
                } else if (sheet.title == null && !text.isBlank() && text.length() >= 6
                        && !text.contains("汇报人") && !text.contains("职务") && !text.contains("日期")
                        && (text.contains("日报") || text.contains("日志") || text.contains("工作"))) {
                    sheet.title = text;
                }
            }
        }
    }

    private String afterLabel(String text, String label, RowSnap snap, int col) {
        int idx = text.indexOf(label);
        String rest = text.substring(idx + label.length());
        rest = rest.replace("：", "").replace(":", "").trim();
        if (!rest.isEmpty()) {
            return rest;
        }
        for (Map.Entry<Integer, CellSnap> entry : snap.cells.entrySet()) {
            if (entry.getKey() > col && entry.getValue().text != null
                    && !entry.getValue().text.contains("职务") && !entry.getValue().text.contains("日期")) {
                return entry.getValue().text;
            }
        }
        return null;
    }

    private Map<String, Integer> mapColumns(RowSnap header) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (CellSnap cell : header.cells.values()) {
            String key = columnKey(cell.text);
            if (key != null && !columns.containsKey(key)) {
                columns.put(key, cell.col);
            }
        }
        return columns;
    }

    private String columnKey(String text) {
        String t = normalize(text);
        if (t.isEmpty()) {
            return null;
        }
        if (t.contains("序号")) {
            return "seq";
        }
        if (t.contains("工作内容")) {
            return "content";
        }
        if (t.contains("时间")) {
            return "time";
        }
        if (t.contains("负责人")) {
            return "owner";
        }
        if (t.contains("责任人")) {
            return "assignee";
        }
        if (t.contains("参与")) {
            return "participants";
        }
        if (t.contains("问题")) {
            return "problem";
        }
        if (t.contains("解决方案") || t.contains("措施")) {
            return "solution";
        }
        if (t.contains("汇报结果") || t.contains("结果")) {
            return "result";
        }
        if (t.contains("备注")) {
            return "remark";
        }
        return null;
    }

    private void readBody(List<RowSnap> body, Map<String, Integer> columns, ParsedSheet sheet) {
        int seqCol = columns.getOrDefault("seq", -1);
        int contentCol = columns.getOrDefault("content", -1);
        ParsedRow current = null;
        int i = 0;
        while (i < body.size()) {
            RowSnap snap = body.get(i);
            if (snap.isEmpty()) {
                i++;
                continue;
            }
            if (isProductionTitle(body, i)) {
                i = readProduction(body, i, sheet);
                continue;
            }
            if (snap.containsText("序号") && snap.containsText("工作内容")) {
                i++;
                continue;
            }
            Integer seq = seqCol >= 0 ? seqOf(snap.textAt(seqCol)) : null;
            String content = contentCol >= 0 ? snap.textAt(contentCol) : null;
            // 工作内容为空的行一律视为上一行被合并单元格折下来的续行，避免把续行当成新工单
            if (content == null) {
                if (current != null) {
                    appendContinuation(current, snap, columns, body.get(i).rowNo);
                }
                i++;
                continue;
            }
            ParsedRow row = new ParsedRow();
            row.rowNo = snap.rowNo + 1;
            row.seq = seq;
            row.content = content;
            row.timeText = text(snap, columns, "time");
            row.ownerText = text(snap, columns, "owner");
            row.assigneeText = text(snap, columns, "assignee");
            row.participantsText = text(snap, columns, "participants");
            row.problemText = text(snap, columns, "problem");
            row.solutionText = text(snap, columns, "solution");
            row.resultText = text(snap, columns, "result");
            row.remark = text(snap, columns, "remark");
            row.rawCells = snap.rawCells();
            row.fingerprint = fingerprint(sheet.name, seq, content);
            sheet.rows.add(row);
            current = row;
            i++;
        }
    }

    private void appendContinuation(ParsedRow row, RowSnap snap, Map<String, Integer> columns, int rowNo) {
        row.problemText = append(row.problemText, textOnly(snap, columns, "problem"));
        row.solutionText = append(row.solutionText, textOnly(snap, columns, "solution"));
        row.resultText = append(row.resultText, textOnly(snap, columns, "result"));
        row.remark = append(row.remark, textOnly(snap, columns, "remark"));
        row.timeText = append(row.timeText, textOnly(snap, columns, "time"));
        if (row.assigneeText == null) {
            row.assigneeText = textOnly(snap, columns, "assignee");
        }
        for (String raw : snap.rawCells()) {
            row.rawCells.add("R" + (rowNo + 1) + ":" + raw);
        }
    }

    /** 续行只取文字格：合并单元格折下来的日期与数字会把汇报结果写成 46323 这样的噪声 */
    private String textOnly(RowSnap snap, Map<String, Integer> columns, String key) {
        Integer col = columns.get(key);
        if (col == null) {
            return null;
        }
        CellSnap cell = snap.cells.get(col);
        if (cell == null || cell.num != null || cell.date != null) {
            return null;
        }
        return cell.text;
    }

    // ----------------------------------------------------------- 生产块

    private boolean isProductionTitle(List<RowSnap> body, int index) {
        RowSnap snap = body.get(index);
        if (!snap.containsText("车间") && !snap.containsText("生产情况")) {
            return false;
        }
        for (int j = index + 1; j < body.size(); j++) {
            RowSnap next = body.get(j);
            if (next.isEmpty()) {
                continue;
            }
            return next.dateCell() != null && next.textCells().size() >= 2;
        }
        return false;
    }

    private int readProduction(List<RowSnap> body, int start, ParsedSheet sheet) {
        String workshop = body.get(start).anyText();
        if (workshop != null && workshop.contains("生产情况")) {
            workshop = null;
        }
        Map<Integer, String> processColumns = null;
        LocalDate statDate = null;
        int i = start + 1;
        while (i < body.size()) {
            RowSnap snap = body.get(i);
            if (snap.isEmpty()) {
                i++;
                continue;
            }
            CellSnap dateCell = snap.dateCell();
            if (dateCell != null) {
                statDate = toDate(dateCell);
                List<CellSnap> processes = snap.textCellsExcept(dateCell.col);
                if (processes.size() >= 2) {
                    processColumns = new HashMap<>();
                    for (CellSnap cell : processes) {
                        processColumns.put(cell.col, cell.text);
                    }
                    i++;
                    continue;
                }
            }
            if (statDate == null || processColumns == null) {
                break;
            }
            if (snap.isLabelRow()) {
                i++;
                continue;
            }
            // 生产块后面会再出现明细行（含序号与工作内容），遇到多文字格的行就交还给明细解析
            if (dateCell == null && snap.textCells().size() >= 2) {
                break;
            }
            if (snap.numericCells().isEmpty()) {
                break;
            }
            for (Map.Entry<Integer, String> entry : processColumns.entrySet()) {
                BigDecimal plan = snap.numberAt(entry.getKey());
                BigDecimal act = snap.numberAt(entry.getKey() + 1);
                if (plan == null && act == null) {
                    continue;
                }
                ParsedMetric metric = new ParsedMetric();
                metric.statDate = statDate;
                metric.workshop = workshop;
                metric.process = entry.getValue();
                metric.planQty = plan;
                metric.actQty = act;
                sheet.metrics.add(metric);
            }
            i++;
        }
        return i;
    }

    private LocalDate toDate(CellSnap cell) {
        return cell.asDate();
    }

    // ----------------------------------------------------------- 工具

    private String text(RowSnap snap, Map<String, Integer> columns, String key) {
        Integer col = columns.get(key);
        return col == null ? null : snap.textAt(col);
    }

    private String append(String origin, String more) {
        if (more == null || more.isBlank()) {
            return origin;
        }
        return origin == null || origin.isBlank() ? more : origin + "\n" + more;
    }

    private Integer seqOf(String text) {
        if (text == null) {
            return null;
        }
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty() || digits.length() > 4) {
            return null;
        }
        return Integer.valueOf(digits);
    }

    private String signature(Map<String, Integer> columns) {
        StringBuilder sb = new StringBuilder();
        columns.forEach((key, col) -> sb.append(key).append(':').append(col).append(','));
        return sb.length() == 0 ? "" : sb.substring(0, sb.length() - 1);
    }

    private String fingerprint(String sheet, Integer seq, String content) {
        String seed = sheet + "|" + (seq == null ? "" : seq) + "|" + (content == null ? "" : normalize(content));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(seed.hashCode());
        }
    }

    private String clean(String text) {
        if (text == null) {
            return null;
        }
        String t = text.replace('\u00A0', ' ').replace("\r", "").trim();
        if (t.isEmpty()) {
            return null;
        }
        for (String token : ERROR_TOKENS) {
            if (t.equals(token)) {
                return null;
            }
        }
        return t;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[\\s\\u3000]", "").replace("：", "").replace(":", "");
    }

    private static final class CellSnap {

        private static final DateTimeFormatter ISO_TEXT = DateTimeFormatter.ofPattern("yyyy-M-d");
        private static final DateTimeFormatter CN_TEXT = DateTimeFormatter.ofPattern("yyyy年M月d日");

        final int col;
        final String text;
        final Double num;
        final LocalDate date;

        CellSnap(int col, String text, Double num, LocalDate date) {
            this.col = col;
            this.text = text;
            this.num = num;
            this.date = date;
        }

        /** 日期既可能是序列号也可能是文本，导出件回导时靠这一条把文本日期认出来 */
        LocalDate asDate() {
            if (date != null) {
                return date;
            }
            if (num != null && num >= 20000 && num < 80000) {
                return EXCEL_EPOCH.plusDays(num.longValue());
            }
            if (text == null || text.isBlank()) {
                return null;
            }
            String t = text.trim();
            try {
                if (t.matches("[0-9]{4}-[0-9]{1,2}-[0-9]{1,2}")) {
                    return LocalDate.parse(t, ISO_TEXT);
                }
                if (t.matches("[0-9]{4}年[0-9]{1,2}月[0-9]{1,2}日")) {
                    return LocalDate.parse(t, CN_TEXT);
                }
            } catch (Exception ignored) {
                return null;
            }
            return null;
        }

        BigDecimal asNumber() {
            if (num != null) {
                return BigDecimal.valueOf(num);
            }
            if (text != null && text.trim().matches("[0-9]+(\\.[0-9]+)?")) {
                return new BigDecimal(text.trim());
            }
            return null;
        }
    }

    private static final class RowSnap {

        final int rowNo;
        final Map<Integer, CellSnap> cells = new LinkedHashMap<>();

        RowSnap(int rowNo) {
            this.rowNo = rowNo;
        }

        boolean isEmpty() {
            return cells.isEmpty();
        }

        String textAt(int col) {
            CellSnap cell = cells.get(col);
            return cell == null ? null : cell.text;
        }

        String anyText() {
            return cells.values().stream().map(c -> c.text).filter(t -> t != null).findFirst().orElse(null);
        }

        boolean containsText(String needle) {
            return cells.values().stream().anyMatch(c -> c.text != null && normalize(c.text).contains(needle));
        }

        List<CellSnap> textCells() {
            List<CellSnap> list = new ArrayList<>();
            for (CellSnap cell : cells.values()) {
                if (cell.text != null && !cell.text.isBlank() && !isNumberText(cell)) {
                    list.add(cell);
                }
            }
            return list;
        }

        List<CellSnap> textCellsExcept(int col) {
            List<CellSnap> list = new ArrayList<>();
            for (CellSnap cell : cells.values()) {
                if (cell.col != col && cell.text != null && !isNumberText(cell)) {
                    list.add(cell);
                }
            }
            return list;
        }

        List<CellSnap> numericCells() {
            List<CellSnap> list = new ArrayList<>();
            for (CellSnap cell : cells.values()) {
                if (cell.asNumber() != null && cell.asDate() == null) {
                    list.add(cell);
                }
            }
            return list;
        }

        int dateCellCount(CellSnap cell) {
            return cell.asDate() != null ? 1 : 0;
        }

        CellSnap dateCell() {
            for (CellSnap cell : cells.values()) {
                if (cell.asDate() != null) {
                    return cell;
                }
            }
            return null;
        }

        boolean isLabelRow() {
            int labels = 0;
            for (CellSnap cell : cells.values()) {
                if (cell.text != null && (cell.text.equals("计划") || cell.text.equals("完成"))) {
                    labels++;
                }
            }
            return labels >= 2;
        }

        BigDecimal numberAt(int col) {
            CellSnap cell = cells.get(col);
            if (cell == null || cell.asDate() != null) {
                return null;
            }
            return cell.asNumber();
        }

        List<String> rawCells() {
            List<String> list = new ArrayList<>();
            for (Map.Entry<Integer, CellSnap> entry : cells.entrySet()) {
                list.add("C" + (entry.getKey() + 1) + ":" + entry.getValue().text);
            }
            return list;
        }

        private boolean isNumberText(CellSnap cell) {
            if (cell.num != null) {
                return true;
            }
            return cell.text != null && cell.text.matches("[0-9.\\-]+");
        }
    }
}
