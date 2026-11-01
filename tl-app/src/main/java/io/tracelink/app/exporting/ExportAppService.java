package io.tracelink.app.exporting;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.audit.model.AuditLog;
import io.tracelink.domain.audit.repository.AuditLogRepository;
import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.exporting.model.ExportSheet;
import io.tracelink.domain.exporting.model.ExportStatus;
import io.tracelink.domain.exporting.model.ExportTask;
import io.tracelink.domain.exporting.model.ExportTemplate;
import io.tracelink.domain.exporting.model.ExportWorkbook;
import io.tracelink.domain.exporting.port.WorkbookWriter;
import io.tracelink.domain.exporting.repository.ExportTaskRepository;
import io.tracelink.domain.org.model.Dept;
import io.tracelink.domain.org.model.Employee;
import io.tracelink.domain.org.repository.DeptRepository;
import io.tracelink.domain.org.repository.EmployeeRepository;
import io.tracelink.domain.report.model.DailyReport;
import io.tracelink.domain.report.repository.DailyReportRepository;
import io.tracelink.domain.stat.model.TaskMetric;
import io.tracelink.domain.stat.repository.TaskMetricRepository;
import io.tracelink.domain.storage.FileStorage;
import io.tracelink.domain.task.model.Task;
import io.tracelink.domain.task.repository.TaskRepository;
import io.tracelink.app.stats.DeptStat;
import io.tracelink.app.stats.HeatCell;
import io.tracelink.app.stats.KpiView;
import io.tracelink.app.stats.PersonStat;
import io.tracelink.app.stats.ProcessRow;
import io.tracelink.app.stats.RadarDim;
import io.tracelink.app.stats.Slice;
import io.tracelink.app.stats.StatsAppService;
import io.tracelink.app.stats.StatsOverviewView;
import io.tracelink.app.stats.StatsPersonView;
import io.tracelink.app.stats.StatsProcessView;
import io.tracelink.app.stats.TaskRefView;
import io.tracelink.app.stats.TrendPoint;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导出应用服务：五种固定模板，同步生成后落库并保留任务记录。
 * 日报模板与源文件一比一（标题、元信息、10 列表头、表尾生产块），导出件能被 {@link io.tracelink.infra.importing.parser.PoiWorkbookParser}
 * 原样读回，因此同一份日报再次导入只会命中已有工单走归并，不会重复建单。
 */
@Service
public class ExportAppService {

    private static final String ROLE_ADMIN = "SYS_ADMIN";
    private static final String ROLE_MANAGER = "MANAGER";
    private static final String ROLE_OWNER = "OWNER";
    private static final String BIZ_EXPORT = "EXPORT";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DAY_TITLE = DateTimeFormatter.ofPattern("yyyy年M月d日");
    private static final List<String> DAILY_HEADERS = List.of("序号", "工作内容", "时间安排", "负责人", "责任人",
            "参与人", "遇到的问题", "解决方案", "汇报结果（未完成原因/追踪的结果）", "备注：具体数据");

    private final ExportTaskRepository exportTaskRepository;
    private final FileStorage fileStorage;
    private final WorkbookWriter workbookWriter;
    private final DailyReportRepository dailyReportRepository;
    private final TaskMetricRepository taskMetricRepository;
    private final TaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;
    private final DeptRepository deptRepository;
    private final StatsAppService statsAppService;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public ExportAppService(ExportTaskRepository exportTaskRepository,
                            FileStorage fileStorage,
                            WorkbookWriter workbookWriter,
                            DailyReportRepository dailyReportRepository,
                            TaskMetricRepository taskMetricRepository,
                            TaskRepository taskRepository,
                            EmployeeRepository employeeRepository,
                            DeptRepository deptRepository,
                            StatsAppService statsAppService,
                            AuditLogRepository auditLogRepository,
                            ObjectMapper objectMapper) {
        this.exportTaskRepository = exportTaskRepository;
        this.fileStorage = fileStorage;
        this.workbookWriter = workbookWriter;
        this.dailyReportRepository = dailyReportRepository;
        this.taskMetricRepository = taskMetricRepository;
        this.taskRepository = taskRepository;
        this.employeeRepository = employeeRepository;
        this.deptRepository = deptRepository;
        this.statsAppService = statsAppService;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- 生成

    public ExportTaskView create(AuthUser actor, ExportRequest request) {
        requireRole(actor);
        ExportTemplate template = request.template() == null ? ExportTemplate.DAILY : request.template();
        LocalDate from = resolveFrom(template, request);
        LocalDate to = request.to() == null ? from : request.to();
        if (to.isBefore(from)) {
            throw BizException.invalid("结束日期不能早于开始日期");
        }

        ExportWorkbook workbook = switch (template) {
            case DAILY -> buildDaily(from, to, request.employeeId());
            case WEEKLY -> buildWeekly(actor, from, to, request.deptId());
            case LEDGER -> buildLedger(from, to, request.deptId(), request.employeeId());
            case PERF -> buildPerf(actor, from, to, request.deptId());
            case CHART -> buildCharts(actor, from, to, request.deptId());
        };
        int rowCnt = workbook.sheets.stream()
                .mapToInt(s -> s.rows.size() + s.footerRows.size())
                .sum();
        byte[] bytes = workbookWriter.write(workbook);

        String fileName = template.label() + "-" + from
                + (to.equals(from) ? "" : "-至-" + to) + ".xlsx";
        FileStorage.StoredFile stored = fileStorage.store("export", fileName,
                new ByteArrayInputStream(bytes));

        ExportTask task = new ExportTask();
        task.templateCode = template;
        task.params = json(Map.of("from", from.toString(), "to", to.toString(),
                "deptId", String.valueOf(request.deptId()), "employeeId", String.valueOf(request.employeeId())));
        task.status = ExportStatus.DONE;
        task.fileKey = stored.key();
        task.rowCnt = rowCnt;
        task.createdBy = actor == null ? null : actor.employeeId();
        exportTaskRepository.insert(task);
        audit(actor, "CREATE", task.id, Map.of("template", template.name(), "file", fileName));
        return toView(task);
    }

    public List<ExportTaskView> list(AuthUser actor) {
        requireRole(actor);
        return exportTaskRepository.listRecent(50).stream().map(this::toView).toList();
    }

    public DownloadFile download(AuthUser actor, Long id) {
        ExportTask task = exportTaskRepository.findById(id);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "导出件不存在或已被清理");
        }
        if (!isManager(actor) && task.createdBy != null && actor != null
                && !task.createdBy.equals(actor.employeeId())) {
            throw new BizException(ErrorCode.NO_PERMISSION, "只能下载自己发起的导出件");
        }
        Path path = fileStorage.resolve(task.fileKey);
        try {
            byte[] bytes = Files.readAllBytes(path);
            // 存储键带内容哈希前缀（用于幂等），下载时对外只暴露可读文件名
            String fileName = task.fileKey.substring(task.fileKey.indexOf('/') + 1)
                    .replaceFirst("^[0-9a-f]{32,64}-", "");
            return new DownloadFile(fileName, bytes);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "导出件读取失败：" + e.getMessage());
        }
    }

    public record DownloadFile(String fileName, byte[] content) {
    }

    // ---------------------------------------------------------------- 日报模板

    /** 日报回填：区间内一天一页，单日导出时页名与源文件一致，多日导出时前缀日期避免重名 */
    private ExportWorkbook buildDaily(LocalDate from, LocalDate to, Long reporterId) {
        ExportWorkbook workbook = new ExportWorkbook();
        Map<Long, String> names = employeeNames();
        boolean multiDay = !from.equals(to);
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            appendDailyDay(workbook, date, reporterId, names, multiDay);
        }
        return workbook;
    }

    private void appendDailyDay(ExportWorkbook workbook, LocalDate date, Long reporterId,
                                Map<Long, String> names, boolean multiDay) {
        int start = workbook.sheets.size();
        List<DailyReport> reports = dailyReportRepository.listBetween(date, date, reporterId);
        List<TaskMetric> metrics = taskMetricRepository.findProcess(date, date);

        if (!reports.isEmpty()) {
            for (DailyReport report : reports) {
                String reporter = names.getOrDefault(report.reporterId, "汇报人");
                workbook.sheets.add(dailySheet(date, reporter, report.sourceSheet, readPage(report), multiDay));
            }
        } else {
            // 没有日报页时用当天的工单回填，导出的文件依旧可以再导入
            Map<Long, List<Task>> byReporter = new LinkedHashMap<>();
            for (Task task : taskRepository.findCreatedBetween(date.atStartOfDay(),
                    date.plusDays(1).atStartOfDay(), null)) {
                Long key = task.ownerId == null ? task.primaryAssigneeId : task.ownerId;
                byReporter.computeIfAbsent(key, k -> new ArrayList<>()).add(task);
            }
            byReporter.forEach((key, tasks) -> {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Task task : tasks) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("content", task.title);
                    item.put("ownerText", names.getOrDefault(key, null));
                    item.put("assigneeText", task.primaryAssigneeId == null ? null : names.get(task.primaryAssigneeId));
                    item.put("resultText", task.resultSummary);
                    rows.add(item);
                }
                workbook.sheets.add(dailySheet(date, names.getOrDefault(key, "汇报人"), null, rows, multiDay));
            });
        }

        if (workbook.sheets.size() == start) {
            // 这一天没有任何日报与工单，也留一页空表格，保证日期区间可见
            workbook.sheets.add(dailySheet(date, "汇报人", null, List.of(), multiDay));
        }
        // 生产块按同一份指标写到这一天的每一页，保证回导时能重建 task_metric
        for (int i = start; i < workbook.sheets.size(); i++) {
            workbook.sheets.get(i).footerRows.addAll(productionBlock(date, metrics));
        }
    }

    private ExportSheet dailySheet(LocalDate date, String reporter, String sourceSheet,
                                   List<Map<String, Object>> rows, boolean multiDay) {
        ExportSheet sheet = new ExportSheet();
        String base = sourceSheet == null || sourceSheet.isBlank() ? date.format(DAY) + "-" + reporter : sourceSheet;
        sheet.name = multiDay ? date.format(DAY) + "-" + base : base;
        sheet.title = date.format(DAY_TITLE) + "工作日报";
        List<String> meta = new ArrayList<>();
        meta.add("汇报人：");
        meta.add(reporter);
        meta.add("职务：");
        meta.add(null);
        meta.add(null);
        meta.add(null);
        meta.add(null);
        meta.add(null);
        meta.add("日期：" + date.format(DAY));
        sheet.prefixRows.add(meta);
        sheet.headers = new ArrayList<>(DAILY_HEADERS);
        int seq = 1;
        for (Map<String, Object> row : rows) {
            List<String> line = new ArrayList<>();
            line.add(String.valueOf(seq++));
            line.add(text(row.get("content")));
            line.add(text(row.get("timeText")));
            line.add(text(row.get("ownerText")));
            line.add(text(row.get("assigneeText")));
            line.add(text(row.get("participantsText")));
            line.add(text(row.get("problemText")));
            line.add(text(row.get("solutionText")));
            line.add(text(row.get("resultText")));
            line.add(text(row.get("remark")));
            sheet.rows.add(line);
        }
        sheet.columnWidths = new int[]{6, 40, 12, 14, 22, 22, 34, 30, 40, 30};
        return sheet;
    }

    /** 表尾生产块：标题行 + 日期与工序行 + 计划完成成对标签行 + 数值行，与样本同构 */
    private List<List<String>> productionBlock(LocalDate date, List<TaskMetric> metrics) {
        List<List<String>> rows = new ArrayList<>();
        if (metrics == null || metrics.isEmpty()) {
            return rows;
        }
        Map<String, BigDecimal[]> byProcess = new LinkedHashMap<>();
        for (TaskMetric metric : metrics) {
            if (metric.dimCode == null) {
                continue;
            }
            BigDecimal[] pair = byProcess.computeIfAbsent(metric.dimCode, k -> new BigDecimal[2]);
            BigDecimal value = metric.value == null ? BigDecimal.ZERO : metric.value;
            if ("PLAN_QTY".equals(metric.metricCode)) {
                pair[0] = (pair[0] == null ? BigDecimal.ZERO : pair[0]).add(value);
            } else if ("ACT_QTY".equals(metric.metricCode)) {
                pair[1] = (pair[1] == null ? BigDecimal.ZERO : pair[1]).add(value);
            }
        }
        if (byProcess.isEmpty()) {
            return rows;
        }
        List<String> titles = new ArrayList<>();
        titles.add("车间每日生产情况");
        List<String> processRow = new ArrayList<>();
        processRow.add(date.format(DAY));
        List<String> labelRow = new ArrayList<>();
        labelRow.add("");
        List<String> valueRow = new ArrayList<>();
        valueRow.add("");
        for (Map.Entry<String, BigDecimal[]> entry : byProcess.entrySet()) {
            processRow.add(entry.getKey());
            processRow.add(null);
            labelRow.add("计划");
            labelRow.add("完成");
            valueRow.add(number(entry.getValue()[0]));
            valueRow.add(number(entry.getValue()[1]));
        }
        rows.add(titles);
        rows.add(processRow);
        rows.add(labelRow);
        rows.add(valueRow);
        rows.add(new ArrayList<>());
        return rows;
    }

    // ---------------------------------------------------------------- 周报模板

    private ExportWorkbook buildWeekly(AuthUser actor, LocalDate from, LocalDate to, Long deptId) {
        StatsOverviewView overview = statsAppService.computeOverview(from, to, deptId);
        ExportWorkbook workbook = new ExportWorkbook();

        ExportSheet kpi = workbook.add("关键指标");
        kpi.title = "部门周报 · " + from + " 至 " + to;
        kpi.headers = List.of("指标", "数值", "单位", "口径");
        for (KpiView view : overview.kpis()) {
            kpi.rows.add(List.of(view.label(), String.valueOf(view.value()), view.unit(), view.formula()));
        }

        ExportSheet depts = workbook.add("部门对比");
        depts.headers = List.of("部门", "新增", "完成", "在办", "逾期", "完成率(%)", "按时完成率(%)");
        for (DeptStat stat : overview.depts()) {
            depts.rows.add(List.of(stat.name(), String.valueOf(stat.created()), String.valueOf(stat.done()),
                    String.valueOf(stat.open()), String.valueOf(stat.overdue()),
                    String.valueOf(stat.completionRate()), String.valueOf(stat.onTimeRate())));
        }
        depts.columnWidths = new int[]{18, 8, 8, 8, 8, 12, 14};

        ExportSheet types = workbook.add("问题类型前十");
        types.headers = List.of("类型", "数量");
        for (Slice slice : overview.types()) {
            types.rows.add(List.of(slice.label(), String.valueOf(slice.count())));
        }

        ExportSheet trend = workbook.add("新增与完成趋势");
        trend.headers = List.of("日期", "新增", "完成", "在办");
        for (TrendPoint point : overview.trend()) {
            trend.rows.add(List.of(point.date(), String.valueOf(point.created()),
                    String.valueOf(point.done()), String.valueOf(point.open())));
        }
        return workbook;
    }

    // ---------------------------------------------------------------- 台账模板

    private ExportWorkbook buildLedger(LocalDate from, LocalDate to, Long deptId, Long employeeId) {
        ExportWorkbook workbook = new ExportWorkbook();
        ExportSheet sheet = workbook.add("工单台账");
        sheet.title = "工单台账 · " + from + " 至 " + to;
        sheet.headers = List.of("工单号", "标题", "类型", "来源", "部门", "负责人", "主责人", "状态",
                "优先级", "计划完成", "实际完成", "进度(%)", "逾期次数", "退回次数", "催办次数", "结果摘要");
        Map<Long, String> depts = deptNames();
        Map<Long, String> names = employeeNames();
        // 台账口径：区间内新建的工单，加上计划完成落在区间内的工单（补录的历史明细也要能看到）
        Map<Long, Task> scope = new LinkedHashMap<>();
        for (Task task : taskRepository.findCreatedBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay(), deptId)) {
            scope.put(task.id, task);
        }
        for (Task task : taskRepository.findPlannedBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay(), deptId)) {
            scope.put(task.id, task);
        }
        List<Task> tasks = new ArrayList<>(scope.values());
        tasks.sort(Comparator.comparing(t -> t.taskNo == null ? "" : t.taskNo));
        int count = 0;
        for (Task task : tasks) {
            if (employeeId != null && !employeeId.equals(task.primaryAssigneeId)
                    && !employeeId.equals(task.ownerId)) {
                continue;
            }
            sheet.rows.add(List.of(
                    task.taskNo == null ? "" : task.taskNo,
                    task.title == null ? "" : task.title,
                    task.type == null ? "" : task.type.label(),
                    task.sourceType == null ? "" : task.sourceType.name(),
                    task.deptId == null ? "" : depts.getOrDefault(task.deptId, ""),
                    task.ownerId == null ? "" : names.getOrDefault(task.ownerId, ""),
                    task.primaryAssigneeId == null ? "" : names.getOrDefault(task.primaryAssigneeId, ""),
                    task.status == null ? "" : task.status.label(),
                    task.priority == null ? "" : task.priority.toString(),
                    task.planEnd == null ? "" : task.planEnd.format(TIME),
                    task.actualEnd == null ? "" : task.actualEnd.format(TIME),
                    String.valueOf(task.progressValue()),
                    String.valueOf(task.delayValue()),
                    String.valueOf(task.rejectValue()),
                    String.valueOf(task.urgeValue()),
                    task.resultSummary == null ? "" : task.resultSummary));
            count++;
        }
        sheet.columnWidths = new int[]{18, 36, 10, 10, 16, 12, 12, 10, 8, 16, 16, 8, 8, 8, 8, 40};
        if (count == 0) {
            sheet.rows.add(List.of("（区间内没有工单）", "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""));
        }
        return workbook;
    }

    // ---------------------------------------------------------------- 绩效模板

    private ExportWorkbook buildPerf(AuthUser actor, LocalDate from, LocalDate to, Long deptId) {
        StatsPersonView person = statsAppService.computePerson(from, to, deptId, null);
        ExportWorkbook workbook = new ExportWorkbook();
        ExportSheet sheet = workbook.add("人员绩效");
        sheet.title = "人员绩效明细 · " + from + " 至 " + to;
        sheet.headers = List.of("人员", "部门", "新增", "完成", "在办", "逾期", "退回",
                "按时完成率(%)", "结果留痕率(%)", "平均完成时长(小时)");
        for (PersonStat stat : person.persons()) {
            sheet.rows.add(List.of(stat.name(), stat.deptName() == null ? "" : stat.deptName(),
                    String.valueOf(stat.created()), String.valueOf(stat.done()), String.valueOf(stat.open()),
                    String.valueOf(stat.overdue()), String.valueOf(stat.rejected()),
                    String.valueOf(stat.onTimeRate()), String.valueOf(stat.evidenceRate()),
                    String.valueOf(stat.avgHours())));
        }
        sheet.columnWidths = new int[]{14, 16, 8, 8, 8, 8, 8, 14, 14, 18};

        ExportSheet radar = workbook.add("能力雷达");
        radar.headers = List.of("维度", "得分");
        for (RadarDim dim : person.radar()) {
            radar.rows.add(List.of(dim.label(), String.valueOf(dim.value())));
        }
        return workbook;
    }

    // ---------------------------------------------------------------- 图表模板

    private ExportWorkbook buildCharts(AuthUser actor, LocalDate from, LocalDate to, Long deptId) {
        StatsOverviewView overview = statsAppService.computeOverview(from, to, deptId);
        StatsProcessView process = statsAppService.computeProcess(from, to);
        StatsPersonView person = statsAppService.computePerson(from, to, deptId, null);
        ExportWorkbook workbook = new ExportWorkbook();

        ExportSheet kpi = workbook.add("01 指标汇总");
        kpi.title = "统计图表报告 · " + from + " 至 " + to;
        kpi.headers = List.of("指标", "数值", "单位", "口径");
        for (KpiView view : overview.kpis()) {
            kpi.rows.add(List.of(view.label(), String.valueOf(view.value()), view.unit(), view.formula()));
        }

        ExportSheet trend = workbook.add("02 新增与完成趋势");
        trend.headers = List.of("日期", "新增", "完成", "在办");
        for (TrendPoint point : overview.trend()) {
            trend.rows.add(List.of(point.date(), String.valueOf(point.created()),
                    String.valueOf(point.done()), String.valueOf(point.open())));
        }

        ExportSheet depts = workbook.add("03 部门完成率");
        depts.headers = List.of("部门", "新增", "完成", "完成率(%)", "按时完成率(%)");
        for (DeptStat stat : overview.depts()) {
            depts.rows.add(List.of(stat.name(), String.valueOf(stat.created()), String.valueOf(stat.done()),
                    String.valueOf(stat.completionRate()), String.valueOf(stat.onTimeRate())));
        }

        ExportSheet status = workbook.add("04 状态分布");
        status.headers = List.of("状态", "单数");
        for (Slice slice : overview.status()) {
            status.rows.add(List.of(slice.label(), String.valueOf(slice.count())));
        }

        ExportSheet bins = workbook.add("05 逾期时长直方");
        bins.headers = List.of("区间", "单数");
        for (Slice slice : overview.overdueBins()) {
            bins.rows.add(List.of(slice.label(), String.valueOf(slice.count())));
        }

        ExportSheet top = workbook.add("06 逾期工单前十");
        top.headers = List.of("工单号", "标题", "部门", "主责人", "状态", "逾期天数", "计划完成");
        for (TaskRefView ref : overview.overdueTop()) {
            top.rows.add(List.of(ref.taskNo() == null ? "" : ref.taskNo(),
                    ref.title() == null ? "" : ref.title(),
                    ref.deptName() == null ? "" : ref.deptName(),
                    ref.assigneeName() == null ? "" : ref.assigneeName(),
                    ref.statusLabel() == null ? "" : ref.statusLabel(),
                    String.valueOf(ref.overdueDays()),
                    ref.planEnd() == null ? "" : ref.planEnd()));
        }

        ExportSheet recurrent = workbook.add("07 复发事项榜");
        recurrent.headers = List.of("事项", "复发次数");
        for (Slice slice : overview.recurrent()) {
            recurrent.rows.add(List.of(slice.label(), String.valueOf(slice.count())));
        }

        ExportSheet types = workbook.add("08 问题类型前十");
        types.headers = List.of("类型", "数量");
        for (Slice slice : overview.types()) {
            types.rows.add(List.of(slice.label(), String.valueOf(slice.count())));
        }

        ExportSheet processes = workbook.add("09 工序计划与完成");
        processes.headers = List.of("日期", "工序", "计划", "完成", "达成率(%)");
        for (ProcessRow row : process.rows()) {
            processes.rows.add(List.of(row.date(), row.process(), String.valueOf(row.planned()),
                    String.valueOf(row.actual()), String.valueOf(row.rate())));
        }

        ExportSheet persons = workbook.add("10 人员产出散点");
        persons.headers = List.of("人员", "部门", "完成", "在办", "逾期", "按时完成率(%)");
        for (PersonStat stat : person.persons()) {
            persons.rows.add(List.of(stat.name(), stat.deptName() == null ? "" : stat.deptName(),
                    String.valueOf(stat.done()), String.valueOf(stat.open()),
                    String.valueOf(stat.overdue()), String.valueOf(stat.onTimeRate())));
        }

        ExportSheet radar = workbook.add("11 人员能力雷达");
        radar.headers = List.of("维度", "得分");
        for (RadarDim dim : person.radar()) {
            radar.rows.add(List.of(dim.label(), String.valueOf(dim.value())));
        }

        ExportSheet funnel = workbook.add("12 完成漏斗");
        funnel.headers = List.of("阶段", "单数");
        for (Slice slice : person.funnel()) {
            funnel.rows.add(List.of(slice.label(), String.valueOf(slice.count())));
        }

        ExportSheet heat = workbook.add("13 部门周热力");
        heat.headers = List.of("部门", "周", "逾期单数");
        for (HeatCell cell : overview.heatmap()) {
            heat.rows.add(List.of(cell.row(), cell.column(), String.valueOf(cell.value())));
        }
        return workbook;
    }

    // ---------------------------------------------------------------- 工具

    private List<Map<String, Object>> readPage(DailyReport report) {
        if (report.raw == null || report.raw.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> page = objectMapper.readValue(report.raw,
                    new TypeReference<Map<String, Object>>() {
                    });
            Object rows = page.get("rows");
            if (rows instanceof List<?> list) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        map.forEach((k, v) -> row.put(String.valueOf(k), v));
                        result.add(row);
                    }
                }
                return result;
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return List.of();
    }

    private LocalDate resolveFrom(ExportTemplate template, ExportRequest request) {
        if (template == ExportTemplate.DAILY) {
            if (request.reportDate() != null) {
                return request.reportDate();
            }
            if (request.from() != null) {
                return request.from();
            }
            return LocalDate.now();
        }
        return request.from() == null ? LocalDate.now().minusDays(29) : request.from();
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String number(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private Map<Long, String> deptNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Dept dept : deptRepository.findAll()) {
            names.put(dept.getId(), dept.getName());
        }
        return names;
    }

    private Map<Long, String> employeeNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Employee employee : employeeRepository.findAll()) {
            names.put(employee.getId(), employee.getName());
        }
        return names;
    }

    private boolean isManager(AuthUser actor) {
        return actor != null && (actor.roles().contains(ROLE_ADMIN) || actor.roles().contains(ROLE_MANAGER));
    }

    private void requireRole(AuthUser actor) {
        if (actor == null || !(isManager(actor) || actor.roles().contains(ROLE_OWNER))) {
            throw new BizException(ErrorCode.NO_PERMISSION, "导出仅对负责人及以上开放");
        }
    }

    private ExportTaskView toView(ExportTask task) {
        String fileName = task.fileKey == null ? null : task.fileKey.substring(task.fileKey.indexOf('/') + 1)
                .replaceFirst("^[0-9a-f]{32,64}-", "");
        return new ExportTaskView(task.id,
                task.templateCode == null ? null : task.templateCode.name(),
                task.templateCode == null ? null : task.templateCode.label(),
                task.status == null ? null : task.status.name(),
                task.status == null ? null : task.status.label(),
                fileName, task.rowCnt,
                task.createdAt == null ? null : task.createdAt.format(TIME),
                task.errorMsg);
    }

    private void audit(AuthUser actor, String action, Long bizId, Map<String, Object> after) {
        if (actor == null) {
            return;
        }
        AuditLog log = new AuditLog();
        log.actorId = actor.employeeId();
        log.action = action;
        log.bizType = BIZ_EXPORT;
        log.bizId = bizId;
        log.after = json(after);
        auditLogRepository.insert(log);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
