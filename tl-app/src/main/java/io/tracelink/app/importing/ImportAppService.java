package io.tracelink.app.importing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.audit.model.AuditLog;
import io.tracelink.domain.audit.repository.AuditLogRepository;
import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.exporting.model.ExportSheet;
import io.tracelink.domain.exporting.model.ExportWorkbook;
import io.tracelink.domain.exporting.port.WorkbookWriter;
import io.tracelink.domain.importing.model.ImportBatch;
import io.tracelink.domain.importing.model.ImportRow;
import io.tracelink.domain.importing.model.ImportStatus;
import io.tracelink.domain.importing.model.RowAction;
import io.tracelink.domain.importing.model.RowLevel;
import io.tracelink.domain.importing.model.RowPlan;
import io.tracelink.domain.importing.repository.ImportBatchRepository;
import io.tracelink.domain.importing.repository.ImportMappingRepository;
import io.tracelink.domain.importing.repository.ImportRowRepository;
import io.tracelink.domain.org.model.Employee;
import io.tracelink.domain.org.repository.EmployeeRepository;
import io.tracelink.domain.report.model.DailyReport;
import io.tracelink.domain.report.repository.DailyReportRepository;
import io.tracelink.domain.stat.model.StatDimType;
import io.tracelink.domain.stat.model.TaskMetric;
import io.tracelink.domain.stat.repository.TaskMetricRepository;
import io.tracelink.domain.storage.FileStorage;
import io.tracelink.domain.task.model.ActionStatus;
import io.tracelink.domain.task.model.AssigneeRoleType;
import io.tracelink.domain.task.model.CheckinResultType;
import io.tracelink.domain.task.model.ClosureLevel;
import io.tracelink.domain.task.model.IssueStatus;
import io.tracelink.domain.task.model.Task;
import io.tracelink.domain.task.model.TaskAction;
import io.tracelink.domain.task.model.TaskAssignee;
import io.tracelink.domain.task.model.TaskCheckin;
import io.tracelink.domain.task.model.TaskIssue;
import io.tracelink.domain.task.model.TaskSourceType;
import io.tracelink.domain.task.model.TaskStatus;
import io.tracelink.domain.task.model.TaskType;
import io.tracelink.domain.task.repository.TaskActionRepository;
import io.tracelink.domain.task.repository.TaskAssigneeRepository;
import io.tracelink.domain.task.repository.TaskCheckinRepository;
import io.tracelink.domain.task.repository.TaskIssueRepository;
import io.tracelink.domain.task.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 导入应用服务：上传 → 预览（惰性解析）→ 行校正 → 确认入库 → 报告/撤回。
 * 解析与语义分析在 ImportAnalyzer，这里只负责状态、持久化、幂等与审计。
 */
@Service
public class ImportAppService {

    private static final String BIZ_IMPORT = "IMPORT";
    private static final String ROLE_ADMIN = "SYS_ADMIN";
    private static final String ROLE_MANAGER = "MANAGER";
    private static final String ROLE_OWNER = "OWNER";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_BATCHES = 50;

    private final ImportBatchRepository batchRepository;
    private final ImportRowRepository rowRepository;
    private final ImportMappingRepository mappingRepository;
    private final DailyReportRepository dailyReportRepository;
    private final TaskMetricRepository taskMetricRepository;
    private final ImportAnalyzer analyzer;
    private final FileStorage fileStorage;
    private final WorkbookWriter workbookWriter;
    private final TaskRepository taskRepository;
    private final TaskCheckinRepository checkinRepository;
    private final TaskIssueRepository issueRepository;
    private final TaskActionRepository actionRepository;
    private final TaskAssigneeRepository assigneeRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public ImportAppService(ImportBatchRepository batchRepository,
                            ImportRowRepository rowRepository,
                            ImportMappingRepository mappingRepository,
                            DailyReportRepository dailyReportRepository,
                            TaskMetricRepository taskMetricRepository,
                            ImportAnalyzer analyzer,
                            FileStorage fileStorage,
                            WorkbookWriter workbookWriter,
                            TaskRepository taskRepository,
                            TaskCheckinRepository checkinRepository,
                            TaskIssueRepository issueRepository,
                            TaskActionRepository actionRepository,
                            TaskAssigneeRepository assigneeRepository,
                            EmployeeRepository employeeRepository,
                            AuditLogRepository auditLogRepository,
                            ObjectMapper objectMapper) {
        this.batchRepository = batchRepository;
        this.rowRepository = rowRepository;
        this.mappingRepository = mappingRepository;
        this.dailyReportRepository = dailyReportRepository;
        this.taskMetricRepository = taskMetricRepository;
        this.analyzer = analyzer;
        this.fileStorage = fileStorage;
        this.workbookWriter = workbookWriter;
        this.taskRepository = taskRepository;
        this.checkinRepository = checkinRepository;
        this.issueRepository = issueRepository;
        this.actionRepository = actionRepository;
        this.assigneeRepository = assigneeRepository;
        this.employeeRepository = employeeRepository;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------- 上传与批次

    @Transactional
    public ImportBatchView upload(AuthUser actor, String fileName, InputStream content) {
        requireRole(actor);
        if (fileName == null || fileName.isBlank()) {
            throw BizException.invalid("请选择要上传的 xlsx 文件");
        }
        if (!fileName.toLowerCase().endsWith(".xlsx")) {
            throw BizException.invalid("只支持 .xlsx 格式，请另存为 Excel 工作簿后重试");
        }
        FileStorage.StoredFile stored = fileStorage.store("import", fileName, content);
        ImportBatch existing = batchRepository.findByHash(stored.sha256());
        if (existing != null) {
            return toView(existing, true);
        }
        ImportBatch batch = new ImportBatch();
        batch.fileHash = stored.sha256();
        batch.fileName = fileName;
        batch.fileKey = stored.key();
        batch.mode = "DAILY_REPORT";
        batch.status = ImportStatus.UPLOADED;
        batch.createdBy = actor.employeeId();
        batchRepository.insert(batch);
        audit(actor, "CREATE", batch.id, Map.of("file", fileName, "hash", stored.sha256()));
        return toView(batch, false);
    }

    public List<ImportBatchView> listBatches(AuthUser actor) {
        requireRole(actor);
        return batchRepository.listRecent(MAX_BATCHES).stream().map(b -> toView(b, false)).toList();
    }

    public ImportBatchView batch(AuthUser actor, Long id) {
        requireRole(actor);
        return toView(requireBatch(id), false);
    }

    // -------------------------------------------------------------- 预览

    @Transactional
    public ImportPreviewView preview(AuthUser actor, Long batchId, boolean reanalyze) {
        ImportBatch batch = requireBatch(batchId);
        requireRole(actor);
        if (reanalyze && batch.status != ImportStatus.CONFIRMED) {
            rowRepository.deleteByBatch(batchId);
            batch.status = ImportStatus.UPLOADED;
            batchRepository.updateStatus(batchId, ImportStatus.UPLOADED.name(), null);
        }
        if (batch.status == ImportStatus.UPLOADED || batch.status == ImportStatus.FAILED
                || batch.status == ImportStatus.ANALYZING) {
            analyze(batch, actor);
        }
        List<ImportRow> rows = rowRepository.listByBatch(batchId, null);
        ImportMappingMeta meta = readMeta(batch);
        List<ImportRowView> views = rows.stream().map(this::toRowView).toList();
        ImportPreviewView.RecognitionView recognition = toRecognition(meta, views);
        List<ImportPreviewView.SheetSummary> sheets = meta == null ? List.of() : meta.sheets.stream()
                .map(s -> new ImportPreviewView.SheetSummary(s.name, s.title, s.reporter, s.dateText,
                        s.rows == null ? 0 : s.rows, s.metrics == null ? 0 : s.metrics,
                        Boolean.TRUE.equals(s.empty)))
                .toList();
        List<ImportPreviewView.MetricView> metrics = meta == null ? List.of() : meta.metrics.stream()
                .map(m -> new ImportPreviewView.MetricView(m.statDate, m.workshop, m.process, m.planQty, m.actQty))
                .toList();
        return new ImportPreviewView(toView(batch, false), sheets, views, recognition, metrics,
                meta == null ? null : meta.signature);
    }

    private void analyze(ImportBatch batch, AuthUser actor) {
        try {
            Path file = fileStorage.resolve(batch.fileKey);
            ImportAnalyzer.AnalysisResult result = analyzer.analyze(file, batch.id);
            rowRepository.deleteByBatch(batch.id);
            rowRepository.insertAll(result.rows());
            batch.sheetTotal = (int) result.sheets().size();
            batch.rowTotal = result.rows().size();
            batch.rowOk = count(result.rows(), RowLevel.OK);
            batch.rowWarn = count(result.rows(), RowLevel.WARN);
            batch.rowError = count(result.rows(), RowLevel.ERROR);
            batch.mapping = result.mappingJson();
            batch.status = ImportStatus.PREVIEWED;
            batch.errorMsg = null;
            batchRepository.updateAnalysis(batch);
            if (!result.signature().isBlank()) {
                mappingRepository.upsert(result.signature(), result.mappingJson());
            }
            audit(actor, "UPDATE", batch.id, Map.of("action", "ANALYZE",
                    "rows", String.valueOf(result.rows().size())));
        } catch (BizException e) {
            batch.status = ImportStatus.FAILED;
            batch.errorMsg = trim(e.getMessage(), 480);
            batchRepository.updateStatus(batch.id, ImportStatus.FAILED.name(), batch.errorMsg);
            throw e;
        }
    }

    // -------------------------------------------------------------- 行校正

    @Transactional
    public ImportRowView updateRow(AuthUser actor, Long batchId, Long rowId, RowEditCommand cmd) {
        ImportBatch batch = requireBatch(batchId);
        requireRole(actor);
        if (batch.status == ImportStatus.CONFIRMED) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, "批次已确认入库，不能再修改行");
        }
        ImportRow row = rowRepository.listByBatch(batchId, null).stream()
                .filter(r -> r.id.equals(rowId))
                .findFirst()
                .orElseThrow(() -> BizException.notFound("导入行不存在，请刷新后重试"));
        RowPlan plan = readPlan(row);
        if (cmd.title() != null) {
            String title = cmd.title().trim();
            if (title.length() < 2 || title.length() > 120) {
                throw BizException.invalid("标题长度 2 到 120 字");
            }
            plan.title = title;
        }
        if (cmd.assigneeId() != null && !cmd.assigneeId().isBlank()) {
            Long assigneeId = Long.valueOf(cmd.assigneeId());
            Employee employee = employeeRepository.findById(assigneeId);
            if (employee == null) {
                throw BizException.notFound("指定的责任人不存在");
            }
            plan.assigneeId = employee.getId();
            plan.assigneeName = employee.getName();
            if (plan.deptId == null && employee.getDeptId() != null) {
                plan.deptId = employee.getDeptId();
                plan.deptName = employee.getName();
            }
        }
        if (cmd.deptId() != null && !cmd.deptId().isBlank()) {
            plan.deptId = Long.valueOf(cmd.deptId());
        }
        if (cmd.planEnd() != null && !cmd.planEnd().isBlank()) {
            plan.planEnd = LocalDate.parse(cmd.planEnd());
        }
        if (cmd.resultText() != null) {
            plan.resultText = cmd.resultText().isBlank() ? null : cmd.resultText().trim();
        }
        if (cmd.problemText() != null) {
            plan.problemText = cmd.problemText().isBlank() ? null : cmd.problemText().trim();
        }
        if (cmd.solutionText() != null) {
            plan.solutionText = cmd.solutionText().isBlank() ? null : cmd.solutionText().trim();
        }
        if (cmd.action() != null && !cmd.action().isBlank()) {
            plan.action = RowAction.valueOf(cmd.action().trim().toUpperCase());
        } else if (plan.assigneeId != null && plan.action == RowAction.SKIP
                && plan.mergeReason == null) {
            plan.action = RowAction.CREATE;
        }
        List<String> issues = recomputeIssues(plan);
        plan.issues = issues;
        row.level = issues.stream().anyMatch(s -> s.contains("无法建单") || s.contains("无法识别"))
                ? RowLevel.ERROR
                : (issues.isEmpty() ? RowLevel.OK : RowLevel.WARN);
        row.parsed = json(plan);
        row.issues = json(issues);
        rowRepository.update(row);
        audit(actor, "UPDATE", batch.id, Map.of("action", "ROW", "rowNo", String.valueOf(row.rowNo)));
        return toRowView(row);
    }

    private List<String> recomputeIssues(RowPlan plan) {
        List<String> issues = new ArrayList<>();
        if (plan.title == null || plan.title.isBlank()) {
            issues.add("缺少工作内容，无法建单");
        }
        if (plan.reportDate == null) {
            issues.add("无法识别汇报日期");
        }
        if (plan.assigneeId == null) {
            issues.add("未匹配到责任人，进入待认领队列");
        }
        if (plan.action == RowAction.MERGE && plan.mergeTaskId == null) {
            issues.add("合并目标缺失，请改回新建或跳过");
        }
        if (plan.resultText == null) {
            issues.add("未填写汇报结果，将建为待追踪工单");
        }
        return issues;
    }

    // -------------------------------------------------------------- 确认入库

    @Transactional
    public ImportConfirmResult confirm(AuthUser actor, Long batchId) {
        ImportBatch batch = requireBatch(batchId);
        requireRole(actor);
        if (batch.status == ImportStatus.CONFIRMED) {
            List<ImportRow> done = rowRepository.listByBatch(batchId, null);
            List<Long> ids = done.stream().map(r -> r.taskId).filter(Objects::nonNull).toList();
            return new ImportConfirmResult(batchId, batch.status.name(), 0, 0, 0, 0, ids,
                    "批次已于此前确认，未重复建单");
        }
        if (batch.status != ImportStatus.PREVIEWED) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, "请先完成预览再确认入库");
        }
        List<ImportRow> rows = rowRepository.listBuildable(batchId);
        int created = 0;
        int merged = 0;
        int skipped = 0;
        int failed = 0;
        List<Long> createdIds = new ArrayList<>();
        List<String> failure = new ArrayList<>();
        Map<String, List<RowPlan>> reports = new LinkedHashMap<>();
        for (ImportRow row : rows) {
            if (row.taskId != null) {
                skipped++;
                continue;
            }
            RowPlan plan = readPlan(row);
            if (plan == null) {
                failed++;
                continue;
            }
            try {
                if (plan.action == RowAction.SKIP) {
                    skipped++;
                    continue;
                }
                if (plan.action == RowAction.MERGE) {
                    mergeInto(plan, row, actor);
                    merged++;
                } else {
                    Task task = createTask(plan, row, actor);
                    row.taskId = task.id;
                    createdIds.add(task.id);
                    created++;
                }
                rowRepository.update(row);
                if (plan.reportDate != null) {
                    String key = plan.reportDate + "#" + (plan.reporterId == null ? 0 : plan.reporterId)
                            + "#" + plan.sheet;
                    reports.computeIfAbsent(key, k -> new ArrayList<>()).add(plan);
                }
            } catch (Exception e) {
                failed++;
                failure.add(row.sheet + " R" + row.rowNo + "：" + e.getMessage());
            }
        }
        writeDailyReports(batch, reports, actor);
        writeMetrics(batch);
        batch.rowOk = count(rows, RowLevel.OK);
        batch.rowWarn = count(rows, RowLevel.WARN);
        batch.rowError = count(rows, RowLevel.ERROR);
        batch.status = ImportStatus.CONFIRMED;
        if (!failure.isEmpty()) {
            String joined = String.join("；", failure.subList(0, Math.min(failure.size(), 20)));
            batch.errorMsg = trim(joined, 1000);
        }
        batchRepository.updateAnalysis(batch);
        audit(actor, "UPDATE", batch.id, Map.of("action", "CONFIRM",
                "created", String.valueOf(created), "merged", String.valueOf(merged),
                "skipped", String.valueOf(skipped), "failed", String.valueOf(failed)));
        String message = String.format("新建 %d 单，并入 %d 条追踪，跳过 %d 行，失败 %d 行",
                created, merged, skipped, failed);
        return new ImportConfirmResult(batch.id, batch.status.name(), created, merged, skipped, failed,
                createdIds, message);
    }

    private Task createTask(RowPlan plan, ImportRow row, AuthUser actor) {
        Task task = new Task();
        task.title = plan.title;
        task.description = plan.description;
        task.type = inferType(plan.title, plan.description);
        task.sourceType = TaskSourceType.REPORT;
        // source_ref 是 JSON 列，写「页/行」定位必须序列化成数组，否则 MySQL 直接报 Invalid JSON
        task.sourceRef = refJson(plan.sheet, plan.rowNo);
        task.deptId = plan.deptId;
        task.ownerId = plan.reporterId == null ? actor.employeeId() : plan.reporterId;
        task.primaryAssigneeId = plan.assigneeId;
        task.assigneeText = plan.assigneeText;
        task.priority = 2;
        task.planStart = plan.planStart == null ? null : plan.planStart.atStartOfDay();
        task.planEnd = plan.planEnd == null ? null : plan.planEnd.atTime(18, 0);
        boolean done = plan.resultText != null;
        task.status = done ? TaskStatus.DONE : TaskStatus.PENDING;
        task.actualEnd = done ? task.planEnd : null;
        task.progress = done ? 100 : 0;
        task.resultSummary = plan.resultText;
        task.acceptCriteria = "[]";
        task.delayCount = 0;
        task.rejectCount = 0;
        task.urgeCount = 0;
        task.closureLevel = done ? ClosureLevel.WEAK : null;
        task.recurrenceOf = plan.mergeTaskId;
        task.tags = "IMPORT";
        task.createdBy = actor.employeeId();
        task.createdAt = LocalDateTime.now();
        task.taskNo = taskRepository.nextTaskNo(LocalDate.now());
        taskRepository.insert(task);

        if (done) {
            TaskCheckin checkin = new TaskCheckin();
            checkin.taskId = task.id;
            checkin.employeeId = plan.assigneeId;
            checkin.checkDate = plan.reportDate;
            checkin.progress = 100;
            checkin.content = plan.resultText;
            checkin.resultType = CheckinResultType.RESULT;
            checkin.closureLevel = ClosureLevel.WEAK;
            checkin.createdAt = LocalDateTime.now();
            checkinRepository.insert(checkin);
        } else if (plan.description != null && !plan.description.isBlank()) {
            TaskCheckin checkin = new TaskCheckin();
            checkin.taskId = task.id;
            checkin.employeeId = plan.assigneeId;
            checkin.checkDate = plan.reportDate;
            checkin.progress = 0;
            checkin.content = "日报导入：" + plan.description;
            checkin.resultType = CheckinResultType.PROGRESS;
            checkin.createdAt = LocalDateTime.now();
            checkinRepository.insert(checkin);
        }
        if (plan.problemText != null && !plan.problemText.isBlank()) {
            TaskIssue issue = new TaskIssue();
            issue.taskId = task.id;
            issue.seq = 1;
            issue.content = trim(plan.problemText, 500);
            issue.severity = 2;
            issue.status = IssueStatus.OPEN;
            issueRepository.insert(issue);
        }
        if (plan.solutionText != null && !plan.solutionText.isBlank()) {
            TaskAction action = new TaskAction();
            action.taskId = task.id;
            action.seq = 1;
            action.content = trim(plan.solutionText, 500);
            action.ownerId = plan.assigneeId == null ? task.ownerId : plan.assigneeId;
            action.dueDate = plan.planEnd;
            action.status = ActionStatus.OPEN;
            actionRepository.insert(action);
        }
        for (Long participantId : plan.participantIds) {
            if (participantId == null || participantId.equals(task.primaryAssigneeId)) {
                continue;
            }
            TaskAssignee assignee = new TaskAssignee();
            assignee.taskId = task.id;
            assignee.employeeId = participantId;
            assignee.roleType = AssigneeRoleType.COOPERATE;
            assigneeRepository.insert(assignee);
        }
        return task;
    }

    /** 工单溯源定位序列化成 JSON 数组：["1026/R4"] */
    private String refJson(String sheet, int rowNo) {
        try {
            return objectMapper.writeValueAsString(List.of(sheet + "/R" + rowNo));
        } catch (Exception e) {
            return "[]";
        }
    }

    private void mergeInto(RowPlan plan, ImportRow row, AuthUser actor) {
        Task task = taskRepository.findById(plan.mergeTaskId);
        if (task == null) {
            throw BizException.notFound("合并目标工单不存在或已删除");
        }
        TaskCheckin checkin = new TaskCheckin();
        checkin.taskId = task.id;
        checkin.employeeId = plan.assigneeId == null ? actor.employeeId() : plan.assigneeId;
        checkin.checkDate = plan.reportDate;
        checkin.progress = plan.resultText == null ? Math.min(95, task.progressValue() + 10) : 100;
        checkin.content = plan.resultText != null ? plan.resultText : "日报并入：" + plan.title;
        checkin.blockerText = plan.problemText;
        checkin.nextStep = plan.solutionText;
        checkin.resultType = plan.resultText != null ? CheckinResultType.RESULT : CheckinResultType.PROGRESS;
        checkin.createdAt = LocalDateTime.now();
        checkinRepository.insert(checkin);
        task.progress = Math.max(task.progressValue(), checkin.progress);
        if (plan.resultText != null) {
            task.resultSummary = plan.resultText;
        }
        taskRepository.update(task);
        row.taskId = task.id;
    }

    private void writeDailyReports(ImportBatch batch, Map<String, List<RowPlan>> reports, AuthUser actor) {
        ImportMappingMeta meta = readMeta(batch);
        for (Map.Entry<String, List<RowPlan>> entry : reports.entrySet()) {
            List<RowPlan> plans = entry.getValue();
            if (plans.isEmpty()) {
                continue;
            }
            RowPlan first = plans.get(0);
            DailyReport report = new DailyReport();
            report.reportDate = first.reportDate;
            report.reporterId = first.reporterId == null ? actor.employeeId() : first.reporterId;
            report.sourceSheet = first.sheet;
            report.importBatchId = batch.id;
            report.raw = pageSnapshot(plans, first, meta);
            dailyReportRepository.upsert(report);
        }
    }

    private String pageSnapshot(List<RowPlan> plans, RowPlan first, ImportMappingMeta meta) {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("reportDate", first.reportDate == null ? null : first.reportDate.toString());
        page.put("reporterName", first.reporterName);
        page.put("sheet", first.sheet);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RowPlan plan : plans) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rowNo", plan.rowNo);
            item.put("content", plan.title);
            item.put("timeText", plan.timeText);
            item.put("ownerText", plan.ownerText == null ? plan.reporterName : plan.ownerText);
            item.put("assigneeText", plan.assigneeText);
            item.put("participantsText", String.join("、", plan.participantNames));
            item.put("problemText", plan.problemText);
            item.put("solutionText", plan.solutionText);
            item.put("resultText", plan.resultText);
            item.put("remark", plan.remark);
            rows.add(item);
        }
        page.put("rows", rows);
        if (meta != null && meta.metrics != null) {
            List<Map<String, Object>> metrics = new ArrayList<>();
            for (ImportMappingMeta.Metric metric : meta.metrics) {
                if (metric.statDate == null || !metric.statDate.equals(first.reportDate.toString())) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("statDate", metric.statDate);
                item.put("workshop", metric.workshop);
                item.put("process", metric.process);
                item.put("planQty", metric.planQty);
                item.put("actQty", metric.actQty);
                metrics.add(item);
            }
            page.put("metrics", metrics);
        }
        return json(page);
    }

    private void writeMetrics(ImportBatch batch) {
        ImportMappingMeta meta = readMeta(batch);
        if (meta == null || meta.metrics == null || meta.metrics.isEmpty()) {
            return;
        }
        LocalDate from = meta.metricFrom == null ? null : LocalDate.parse(meta.metricFrom);
        LocalDate to = meta.metricTo == null ? null : LocalDate.parse(meta.metricTo);
        if (from != null && to != null) {
            taskMetricRepository.deleteByDateRange(from, to);
        }
        List<TaskMetric> metrics = new ArrayList<>();
        for (ImportMappingMeta.Metric item : meta.metrics) {
            if (item.statDate == null || item.process == null) {
                continue;
            }
            LocalDate date = LocalDate.parse(item.statDate);
            addMetric(metrics, date, item.process, "PLAN_QTY", item.planQty);
            addMetric(metrics, date, item.process, "ACT_QTY", item.actQty);
        }
        taskMetricRepository.upsertAll(metrics);
    }

    private void addMetric(List<TaskMetric> metrics, LocalDate date, String process, String code, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        TaskMetric metric = new TaskMetric();
        metric.statDate = date;
        metric.dimType = StatDimType.PROCESS;
        metric.dimCode = process;
        metric.metricCode = code;
        metric.value = new BigDecimal(value);
        metric.unit = "件";
        metrics.add(metric);
    }

    // -------------------------------------------------------------- 报告与撤回

    public ReportFile report(AuthUser actor, Long batchId) {
        requireRole(actor);
        ImportBatch batch = requireBatch(batchId);
        List<ImportRow> rows = rowRepository.listByBatch(batchId, null);
        ExportWorkbook workbook = new ExportWorkbook();
        ExportSheet sheet = workbook.add("导入校验报告");
        sheet.title = "导入校验报告 · " + batch.fileName;
        sheet.headers = List.of("工作表", "行号", "级别", "标题", "责任人", "动作", "关联工单", "原始值/原因");
        List<List<String>> data = new ArrayList<>();
        for (ImportRow row : rows) {
            RowPlan plan = readPlan(row);
            data.add(List.of(
                    row.sheet,
                    String.valueOf(row.rowNo),
                    row.level == null ? "" : row.level.label(),
                    plan == null || plan.title == null ? "" : plan.title,
                    plan == null || plan.assigneeName == null ? "" : plan.assigneeName,
                    plan == null || plan.action == null ? "" : plan.action.label(),
                    row.taskId == null ? "" : String.valueOf(row.taskId),
                    String.join("；", readIssues(row.issues))));
        }
        sheet.rows = data;
        sheet.columnWidths = new int[]{14, 8, 8, 36, 12, 8, 12, 50};
        byte[] bytes = workbookWriter.write(workbook);
        String fileName = "导入报告-" + batch.id + ".xlsx";
        return new ReportFile(fileName, bytes);
    }

    @Transactional
    public ImportBatchView revoke(AuthUser actor, Long batchId) {
        ImportBatch batch = requireBatch(batchId);
        requireRole(actor);
        if (batch.status == ImportStatus.CONFIRMED) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, "批次已入库，工单不会随撤回删除，请到工单页处理");
        }
        rowRepository.deleteByBatch(batchId);
        batch.status = ImportStatus.REVOKED;
        batch.rowTotal = 0;
        batch.rowOk = 0;
        batch.rowWarn = 0;
        batch.rowError = 0;
        batchRepository.updateAnalysis(batch);
        audit(actor, "UPDATE", batchId, Map.of("action", "REVOKE"));
        return toView(batch, false);
    }

    public List<DailyReportView> reports(AuthUser actor, LocalDate from, LocalDate to, Long reporterId) {
        List<Employee> employees = employeeRepository.findAll();
        Map<Long, String> names = new LinkedHashMap<>();
        for (Employee e : employees) {
            names.put(e.getId(), e.getName());
        }
        LocalDate start = from == null ? LocalDate.now().minusDays(30) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        return dailyReportRepository.listBetween(start, end, reporterId).stream()
                .map(r -> new DailyReportView(r.id, r.reportDate == null ? null : r.reportDate.toString(),
                        r.reporterId, names.getOrDefault(r.reporterId, "—"), r.sourceSheet,
                        countSnapshot(r.raw, "rows"), countSnapshot(r.raw, "metrics"),
                        r.createdAt == null ? null : TIME.format(r.createdAt)))
                .toList();
    }

    // -------------------------------------------------------------- 视图与工具

    private ImportBatchView toView(ImportBatch batch, boolean duplicate) {
        ImportMappingMeta meta = readMeta(batch);
        return new ImportBatchView(batch.id, batch.fileName,
                batch.status == null ? null : batch.status.name(),
                batch.status == null ? null : batch.status.label(),
                batch.sheetTotal, batch.rowTotal, batch.rowOk, batch.rowWarn, batch.rowError,
                batch.createdAt == null ? null : TIME.format(batch.createdAt),
                batch.errorMsg, duplicate, meta == null ? null : meta.signature);
    }

    private ImportRowView toRowView(ImportRow row) {
        RowPlan plan = readPlan(row);
        return new ImportRowView(row.id, row.sheet, row.rowNo,
                row.level == null ? null : row.level.name(),
                row.level == null ? null : row.level.label(),
                plan == null ? null : plan.title,
                plan == null ? null : plan.reporterName,
                plan == null || plan.reportDate == null ? null : plan.reportDate.toString(),
                plan == null ? null : plan.assigneeText,
                plan == null ? null : plan.assigneeId,
                plan == null ? null : plan.assigneeName,
                plan == null ? List.of() : plan.participantNames,
                plan == null || plan.action == null ? null : plan.action.name(),
                plan == null || plan.action == null ? null : plan.action.label(),
                plan == null ? null : plan.mergeTaskId,
                plan == null ? null : plan.mergeTaskNo,
                plan == null ? null : plan.mergeReason,
                readIssues(row.issues),
                plan == null ? null : plan.problemText,
                plan == null ? null : plan.solutionText,
                plan == null ? null : plan.resultText,
                plan == null || plan.planEnd == null ? null : plan.planEnd.toString(),
                plan == null ? null : plan.timeText,
                row.fingerprint, row.taskId);
    }

    private ImportPreviewView.RecognitionView toRecognition(ImportMappingMeta meta, List<ImportRowView> rows) {
        if (meta != null && meta.recognition != null) {
            ImportMappingMeta.Recognition r = meta.recognition;
            return new ImportPreviewView.RecognitionView(r.detailRows, r.rowsWithAssignee,
                    r.personMentions, r.personMatched, r.rowRate, r.personRate,
                    r.rowTarget, r.personTarget, r.rowPass, r.personPass);
        }
        int detail = rows.size();
        int withAssignee = (int) rows.stream().filter(r -> r.assigneeName() != null).count();
        double rate = detail == 0 ? 0 : (double) withAssignee / detail;
        return new ImportPreviewView.RecognitionView(detail, withAssignee, 0, 0, rate, 0,
                0.95, 0.92, rate >= 0.95, false);
    }

    private ImportBatch requireBatch(Long id) {
        ImportBatch batch = id == null ? null : batchRepository.findById(id);
        if (batch == null) {
            throw BizException.notFound("导入批次不存在，请刷新后重试");
        }
        return batch;
    }

    private ImportMappingMeta readMeta(ImportBatch batch) {
        if (batch.mapping == null || batch.mapping.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(batch.mapping, ImportMappingMeta.class);
        } catch (Exception e) {
            return null;
        }
    }

    private RowPlan readPlan(ImportRow row) {
        if (row.parsed == null || row.parsed.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(row.parsed, RowPlan.class);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> readIssues(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private int countSnapshot(String json, String field) {
        if (json == null || json.isBlank()) {
            return 0;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            Object value = map.get(field);
            return value instanceof List<?> list ? list.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private int count(List<ImportRow> rows, RowLevel level) {
        return (int) rows.stream().filter(r -> r.level == level).count();
    }

    private TaskType inferType(String title, String description) {
        String text = (title == null ? "" : title) + " " + (description == null ? "" : description);
        if (text.contains("巡查") || text.contains("现场") || text.contains("5S") || text.contains("整改")
                || text.contains("目视")) {
            return TaskType.PATROL;
        }
        if (text.contains("质量") || text.contains("检验") || text.contains("不良") || text.contains("返工")) {
            return TaskType.QUALITY;
        }
        if (text.contains("设备") || text.contains("维修") || text.contains("空压") || text.contains("搬迁")) {
            return TaskType.EQUIP;
        }
        if (text.contains("客户") || text.contains("交付") || text.contains("发货")) {
            return TaskType.CUSTOMER;
        }
        if (text.contains("安全") || text.contains("环保") || text.contains("环评")) {
            return TaskType.SAFETY;
        }
        if (text.contains("计划") || text.contains("评审") || text.contains("项目") || text.contains("进度")) {
            return TaskType.PLAN;
        }
        return TaskType.OTHER;
    }

    private void requireRole(AuthUser actor) {
        if (actor.roles().contains(ROLE_ADMIN) || actor.roles().contains(ROLE_MANAGER)
                || actor.roles().contains(ROLE_OWNER)) {
            return;
        }
        throw new BizException(ErrorCode.NO_PERMISSION, "导入导出仅对管理者、负责人开放");
    }

    private void audit(AuthUser actor, String action, Long bizId, Map<String, Object> after) {
        AuditLog log = new AuditLog();
        log.actorId = actor.employeeId();
        log.action = action;
        log.bizType = BIZ_IMPORT;
        log.bizId = bizId;
        log.after = json(after);
        auditLogRepository.insert(log);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "导入数据序列化失败");
        }
    }

    private String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** 下载用的小载体：文件名与内容 */
    public record ReportFile(String fileName, byte[] content) {
    }
}
