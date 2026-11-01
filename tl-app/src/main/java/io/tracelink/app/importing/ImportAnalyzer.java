package io.tracelink.app.importing;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.importing.model.ImportRow;
import io.tracelink.domain.importing.model.RowAction;
import io.tracelink.domain.importing.model.RowLevel;
import io.tracelink.domain.importing.model.RowPlan;
import io.tracelink.domain.importing.port.ParsedMetric;
import io.tracelink.domain.importing.port.ParsedRow;
import io.tracelink.domain.importing.port.ParsedSheet;
import io.tracelink.domain.importing.port.ParsedWorkbook;
import io.tracelink.domain.importing.port.WorkbookParser;
import io.tracelink.domain.importing.repository.ImportRowRepository;
import io.tracelink.domain.org.model.Dept;
import io.tracelink.domain.org.model.Employee;
import io.tracelink.domain.org.repository.DeptRepository;
import io.tracelink.domain.org.repository.EmployeeAliasRepository;
import io.tracelink.domain.org.repository.EmployeeRepository;
import io.tracelink.domain.task.model.Task;
import io.tracelink.domain.task.model.TaskStatus;
import io.tracelink.domain.task.repository.TaskRepository;
import io.tracelink.domain.task.rules.TitleSimilarity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析后的语义分析：把每个明细行变成可入库的 RowPlan，并给出默认归并动作与校验档位。
 * 解析只认结构，这里才认人、认部门、认日期。
 */
@Component
public class ImportAnalyzer {

    private static final double DEFAULT_SIMILARITY = 0.85;
    private static final double ROW_RATE_TARGET = 0.95;
    private static final double PERSON_RATE_TARGET = 0.92;
    private static final int DEDUP_DAYS = 180;
    private static final Pattern DATE_CN = Pattern.compile("(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日?");
    private static final Pattern DATE_DASH = Pattern.compile("(\\d{4})\\s*[-/.]\\s*(\\d{1,2})\\s*[-/.]\\s*(\\d{1,2})");
    private static final Pattern DATE_MD_CN = Pattern.compile("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日?");
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private final WorkbookParser workbookParser;
    private final EmployeeRepository employeeRepository;
    private final EmployeeAliasRepository aliasRepository;
    private final DeptRepository deptRepository;
    private final TaskRepository taskRepository;
    private final ImportRowRepository importRowRepository;
    private final ObjectMapper objectMapper;

    public ImportAnalyzer(WorkbookParser workbookParser,
                          EmployeeRepository employeeRepository,
                          EmployeeAliasRepository aliasRepository,
                          DeptRepository deptRepository,
                          TaskRepository taskRepository,
                          ImportRowRepository importRowRepository,
                          ObjectMapper objectMapper) {
        this.workbookParser = workbookParser;
        this.employeeRepository = employeeRepository;
        this.aliasRepository = aliasRepository;
        this.deptRepository = deptRepository;
        this.taskRepository = taskRepository;
        this.importRowRepository = importRowRepository;
        this.objectMapper = objectMapper;
    }

    public AnalysisResult analyze(Path file, Long batchId) {
        ParsedWorkbook workbook = workbookParser.parse(file);
        List<Employee> employees = employeeRepository.findAll();
        List<Dept> depts = deptRepository.findAll();
        List<String> deptNames = depts.stream().map(Dept::getName).filter(n -> n != null).toList();
        PersonDirectory directory = PersonDirectory.build(employees, aliasRepository.aliases(), deptNames);
        Map<Long, String> deptNameMap = new HashMap<>();
        for (Dept d : depts) {
            deptNameMap.put(d.getId(), d.getName());
        }
        Map<Long, Employee> employeeById = new HashMap<>();
        for (Employee e : employees) {
            employeeById.put(e.getId(), e);
        }

        List<ImportRow> rows = new ArrayList<>();
        List<ImportPreviewView.SheetSummary> summaries = new ArrayList<>();
        List<ParsedMetric> metrics = new ArrayList<>();
        Map<Long, List<Task>> dedupCache = new HashMap<>();
        int personMentions = 0;
        int personMatched = 0;
        int completeRows = 0;
        int withAssignee = 0;
        LocalDate cursor = null;
        int defaultYear = LocalDate.now().getYear();
        int defaultMonth = LocalDate.now().getMonthValue();

        // 先扫一遍元信息里的日期，作为「1026」「29」这类页名的年份与月份参照
        for (ParsedSheet sheet : workbook.sheets) {
            LocalDate meta = parseDate(sheet.dateText, defaultYear);
            if (meta != null) {
                defaultYear = meta.getYear();
                defaultMonth = meta.getMonthValue();
                break;
            }
        }

        for (ParsedSheet sheet : workbook.sheets) {
            LocalDate reportDate = resolveSheetDate(sheet.name, sheet.dateText, cursor, defaultYear, defaultMonth);
            if (reportDate != null) {
                cursor = reportDate;
            }
            String reporterName = sheet.reporterText == null ? null : sheet.reporterText.trim();
            Employee reporter = directory.resolve(reporterName).primary();
            if (sheet.empty) {
                summaries.add(new ImportPreviewView.SheetSummary(sheet.name, sheet.title,
                        reporter == null ? reporterName : reporter.getName(),
                        reportDate == null ? null : reportDate.toString(), 0, 0, true));
                continue;
            }
            for (ParsedRow source : sheet.rows) {
                PersonDirectory.Resolution owner = directory.resolve(source.ownerText);
                PersonDirectory.Resolution assignee = directory.resolve(source.assigneeText);
                PersonDirectory.Resolution participants = directory.resolve(source.participantsText);
                personMentions += owner.mentions() + assignee.mentions() + participants.mentions();
                personMatched += owner.matched().size() + assignee.matched().size() + participants.matched().size();

                RowPlan plan = new RowPlan();
                plan.sheet = sheet.name;
                plan.rowNo = source.rowNo;
                plan.reportDate = reportDate;
                plan.reporterId = reporter == null ? null : reporter.getId();
                plan.reporterName = reporter == null ? reporterName : reporter.getName();
                plan.title = truncate(source.content, 120);
                plan.description = composeDescription(source);
                plan.timeText = source.timeText;
                plan.problemText = source.problemText;
                plan.solutionText = source.solutionText;
                plan.resultText = source.resultText;
                plan.remark = source.remark;
                plan.ownerText = source.ownerText;
                plan.assigneeText = source.assigneeText;

                Employee primaryAssignee = assignee.primary();
                if (primaryAssignee == null && !owner.matched().isEmpty()) {
                    // 负责人列本身就是责任归属；负责人等于汇报人时说明这件事由本人承办，照样落主责，
                    // 否则副总亲自做的工作会全部掉进待认领队列
                    primaryAssignee = owner.primary();
                }
                if (primaryAssignee != null) {
                    plan.assigneeId = primaryAssignee.getId();
                    plan.assigneeName = primaryAssignee.getName();
                }
                Employee deptAnchor = primaryAssignee != null ? primaryAssignee : reporter;
                if (deptAnchor != null && deptAnchor.getDeptId() != null) {
                    plan.deptId = deptAnchor.getDeptId();
                    plan.deptName = deptNameMap.get(deptAnchor.getDeptId());
                }
                for (Employee e : assignee.others(primaryAssignee)) {
                    plan.participantIds.add(e.getId());
                    plan.participantNames.add(e.getName());
                }
                for (Employee e : participants.others(primaryAssignee)) {
                    if (!plan.participantIds.contains(e.getId())) {
                        plan.participantIds.add(e.getId());
                        plan.participantNames.add(e.getName());
                    }
                }
                for (Employee e : owner.others(primaryAssignee)) {
                    if (!plan.participantIds.contains(e.getId())) {
                        plan.participantIds.add(e.getId());
                        plan.participantNames.add(e.getName());
                    }
                }
                plan.planStart = reportDate;
                plan.planEnd = reportDate == null ? null : reportDate;
                // 行指纹在认人认日期之后才算：只用「日期 + 工作内容 + 主责」，页名与行号不参与，
                // 这样导出的日报换个页名回来仍能命中同一行，不会重复建单
                plan.fingerprint = rowFingerprint(plan);

                List<String> issues = new ArrayList<>();
                RowLevel level = RowLevel.OK;
                if (plan.title == null || plan.title.isBlank()) {
                    level = RowLevel.ERROR;
                    issues.add("缺少工作内容，无法建单");
                }
                if (plan.reportDate == null) {
                    level = RowLevel.ERROR;
                    issues.add("无法识别汇报日期，请在工作表名或日期栏补全");
                }
                if (plan.deptId == null) {
                    if (level != RowLevel.ERROR) {
                        level = RowLevel.WARN;
                    }
                    issues.add("未定位到承办部门，默认落在汇报人所在部门");
                }
                if (plan.assigneeId == null) {
                    if (level != RowLevel.ERROR) {
                        level = RowLevel.WARN;
                    }
                    plan.action = RowAction.SKIP;
                    issues.add("未匹配到责任人，进入待认领队列，可在确认页指定");
                } else if (level != RowLevel.ERROR) {
                    decideAction(plan, issues, batchId, dedupCache);
                }
                if (plan.resultText == null && level != RowLevel.ERROR && plan.action != RowAction.SKIP) {
                    if (level == RowLevel.OK) {
                        level = RowLevel.WARN;
                    }
                    issues.add("未填写汇报结果，将建为待追踪工单");
                    plan.planEnd = reportDate == null ? null : reportDate.plusDays(3);
                }
                if (plan.planEnd == null) {
                    plan.planEnd = plan.planStart;
                }
                plan.issues = issues;

                ImportRow row = new ImportRow();
                row.batchId = batchId;
                row.sheet = sheet.name;
                row.rowNo = source.rowNo;
                row.level = level;
                row.fingerprint = plan.fingerprint;
                row.raw = json(Map.of("sheet", sheet.name, "rowNo", source.rowNo, "cells", source.rawCells));
                row.parsed = json(plan);
                row.issues = json(issues);
                rows.add(row);

                if (plan.title != null && !plan.title.isBlank()
                        && (plan.assigneeId != null || plan.resultText != null)) {
                    completeRows++;
                }
                if (plan.assigneeId != null) {
                    withAssignee++;
                }
            }
            metrics.addAll(sheet.metrics);
            summaries.add(new ImportPreviewView.SheetSummary(sheet.name, sheet.title,
                    reporter == null ? reporterName : reporter.getName(),
                    reportDate == null ? null : reportDate.toString(),
                    sheet.rows.size(), sheet.metrics.size(), false));
        }

        int detailRows = rows.size();
        double rowRate = detailRows == 0 ? 0.0 : round((double) completeRows / detailRows);
        double personRate = personMentions == 0 ? 1.0 : round((double) personMatched / personMentions);
        ImportPreviewView.RecognitionView recognition = new ImportPreviewView.RecognitionView(
                detailRows, withAssignee, personMentions, personMatched, rowRate, personRate,
                ROW_RATE_TARGET, PERSON_RATE_TARGET, rowRate >= ROW_RATE_TARGET, personRate >= PERSON_RATE_TARGET);
        String signature = workbook.sheets.stream()
                .map(s -> s.signature)
                .filter(s -> s != null && !s.isBlank())
                .findFirst().orElse("");
        return new AnalysisResult(rows, recognition, summaries, metrics, signature,
                buildMapping(signature, workbook, metrics, recognition));
    }

    /** 跨批次稳定的行指纹：同一天、同一件事、同一主责即视为同一行 */
    private String rowFingerprint(RowPlan plan) {
        String seed = (plan.reportDate == null ? "" : plan.reportDate.toString())
                + "|" + (plan.title == null ? "" : plan.title.replaceAll("\\s", ""))
                + "|" + (plan.assigneeId == null ? "" : plan.assigneeId);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(seed.hashCode());
        }
    }

    private void decideAction(RowPlan plan, List<String> issues, Long batchId,
                              Map<Long, List<Task>> dedupCache) {
        ImportRow duplicate = importRowRepository.findConfirmedByFingerprint(plan.fingerprint, batchId);
        if (duplicate != null) {
            plan.action = RowAction.SKIP;
            plan.mergeTaskId = duplicate.taskId;
            plan.mergeReason = "该行已由其他批次入库，跳过以免重复建单";
            issues.add("已存在同指纹的入库行");
            return;
        }
        List<Task> candidates = dedupCache.computeIfAbsent(plan.deptId,
                dept -> taskRepository.findForDedup(dept, java.time.LocalDateTime.now().minusDays(DEDUP_DAYS)));
        Task best = null;
        double bestScore = 0;
        for (Task task : candidates) {
            if (task.primaryAssigneeId == null || !task.primaryAssigneeId.equals(plan.assigneeId)) {
                continue;
            }
            double score = TitleSimilarity.score(plan.title, task.title);
            if (score > bestScore) {
                bestScore = score;
                best = task;
            }
        }
        if (best == null || bestScore < DEFAULT_SIMILARITY) {
            plan.action = RowAction.CREATE;
            return;
        }
        plan.similarity = round(bestScore);
        plan.mergeTaskId = best.id;
        plan.mergeTaskNo = best.taskNo;
        if (best.status == TaskStatus.DONE) {
            plan.action = RowAction.CREATE;
            plan.mergeReason = "同名事项已验收，按复发新开一单";
            issues.add("该事项 180 天内已验收，新单将标记复发来源");
        } else {
            plan.action = RowAction.MERGE;
            plan.mergeReason = "同名事项仍在办，作为一条追踪记录并入";
        }
    }

    public static LocalDate resolveSheetDate(String sheetName, String dateText, LocalDate cursor,
                                             int defaultYear, int defaultMonth) {
        LocalDate fromText = parseDate(dateText, defaultYear);
        if (fromText != null) {
            return fromText;
        }
        if (sheetName == null) {
            return cursor;
        }
        String name = sheetName.replaceAll("\\s", "");
        Matcher cn = DATE_MD_CN.matcher(name);
        if (cn.find()) {
            return safeDate(defaultYear, Integer.parseInt(cn.group(1)), Integer.parseInt(cn.group(2)));
        }
        Matcher digits = DIGITS.matcher(name);
        if (!digits.matches()) {
            return cursor;
        }
        String num = digits.group();
        if (num.length() == 8) {
            return safeDate(Integer.parseInt(num.substring(0, 4)),
                    Integer.parseInt(num.substring(4, 6)), Integer.parseInt(num.substring(6, 8)));
        }
        if (num.length() == 4) {
            int month = Integer.parseInt(num.substring(0, 2));
            int day = Integer.parseInt(num.substring(2, 4));
            int year = cursor == null ? defaultYear : cursor.getYear();
            return safeDate(year, month, day);
        }
        if (num.length() <= 2) {
            int day = Integer.parseInt(num);
            LocalDate base = cursor;
            if (base == null) {
                base = LocalDate.of(defaultYear, Math.max(1, defaultMonth), 1);
            }
            LocalDate sameMonth = safeDate(base.getYear(), base.getMonthValue(), day);
            if (sameMonth == null) {
                return base;
            }
            if (sameMonth.isBefore(base)) {
                return base.plusMonths(1).withDayOfMonth(Math.min(day, base.plusMonths(1).lengthOfMonth()));
            }
            return sameMonth;
        }
        return cursor;
    }

    private static LocalDate parseDate(String text, int defaultYear) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher cn = DATE_CN.matcher(text);
        if (cn.find()) {
            return safeDate(Integer.parseInt(cn.group(1)), Integer.parseInt(cn.group(2)),
                    Integer.parseInt(cn.group(3)));
        }
        Matcher dash = DATE_DASH.matcher(text);
        if (dash.find()) {
            return safeDate(Integer.parseInt(dash.group(1)), Integer.parseInt(dash.group(2)),
                    Integer.parseInt(dash.group(3)));
        }
        Matcher md = DATE_MD_CN.matcher(text);
        if (md.find()) {
            return safeDate(defaultYear, Integer.parseInt(md.group(1)), Integer.parseInt(md.group(2)));
        }
        if (text.matches("\\d{8}")) {
            return safeDate(Integer.parseInt(text.substring(0, 4)), Integer.parseInt(text.substring(4, 6)),
                    Integer.parseInt(text.substring(6, 8)));
        }
        return null;
    }

    private static LocalDate safeDate(int year, int month, int day) {
        if (month < 1 || month > 12 || day < 1 || day > 31) {
            return null;
        }
        try {
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    private String composeDescription(ParsedRow row) {
        StringBuilder sb = new StringBuilder();
        appendLine(sb, "问题", row.problemText);
        appendLine(sb, "方案", row.solutionText);
        appendLine(sb, "时间安排", row.timeText);
        appendLine(sb, "备注", row.remark);
        return sb.toString().trim();
    }

    private void appendLine(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append("：").append(value.trim()).append('\n');
        }
    }

    private String buildMapping(String signature, ParsedWorkbook workbook,
                                List<ParsedMetric> metrics, ImportPreviewView.RecognitionView recognition) {
        ImportMappingMeta meta = new ImportMappingMeta();
        meta.signature = signature;
        meta.sheetTotal = workbook.sheetTotal();
        for (ParsedSheet sheet : workbook.sheets) {
            ImportMappingMeta.Sheet item = new ImportMappingMeta.Sheet();
            item.name = sheet.name;
            item.headerRow = sheet.headerRowNo;
            item.title = sheet.title;
            item.reporter = sheet.reporterText;
            item.dateText = sheet.dateText;
            item.columns = sheet.signature;
            item.rows = sheet.rows.size();
            item.metrics = sheet.metrics.size();
            item.empty = sheet.empty;
            meta.sheets.add(item);
        }
        ImportMappingMeta.Recognition r = new ImportMappingMeta.Recognition();
        r.detailRows = recognition.detailRows();
        r.rowsWithAssignee = recognition.rowsWithAssignee();
        r.personMentions = recognition.personMentions();
        r.personMatched = recognition.personMatched();
        r.rowRate = recognition.rowRate();
        r.personRate = recognition.personRate();
        r.rowTarget = recognition.rowTarget();
        r.personTarget = recognition.personTarget();
        r.rowPass = recognition.rowPass();
        r.personPass = recognition.personPass();
        meta.recognition = r;
        LocalDate from = null;
        LocalDate to = null;
        for (ParsedMetric metric : metrics) {
            ImportMappingMeta.Metric item = new ImportMappingMeta.Metric();
            item.statDate = metric.statDate == null ? null : metric.statDate.toString();
            item.workshop = metric.workshop;
            item.process = metric.process;
            item.planQty = metric.planQty == null ? null : metric.planQty.toPlainString();
            item.actQty = metric.actQty == null ? null : metric.actQty.toPlainString();
            meta.metrics.add(item);
            if (metric.statDate != null) {
                from = from == null || metric.statDate.isBefore(from) ? metric.statDate : from;
                to = to == null || metric.statDate.isAfter(to) ? metric.statDate : to;
            }
        }
        meta.metricFrom = from == null ? null : from.toString();
        meta.metricTo = to == null ? null : to.toString();
        return json(meta);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "导入数据序列化失败");
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    public record AnalysisResult(List<ImportRow> rows,
                                 ImportPreviewView.RecognitionView recognition,
                                 List<ImportPreviewView.SheetSummary> sheets,
                                 List<ParsedMetric> metrics,
                                 String signature,
                                 String mappingJson) {
    }
}
