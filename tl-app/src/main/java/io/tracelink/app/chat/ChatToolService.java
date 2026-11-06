package io.tracelink.app.chat;

import io.tracelink.app.stats.KpiView;
import io.tracelink.app.stats.PersonStat;
import io.tracelink.app.stats.StatsAppService;
import io.tracelink.app.stats.StatsOverviewView;
import io.tracelink.app.stats.StatsPersonView;
import io.tracelink.app.task.TaskAppService;
import io.tracelink.app.task.TaskView;
import io.tracelink.common.api.PageResult;
import io.tracelink.domain.ai.ChatPort;
import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.org.model.Dept;
import io.tracelink.domain.org.model.Employee;
import io.tracelink.domain.org.repository.DeptRepository;
import io.tracelink.domain.org.repository.EmployeeRepository;
import io.tracelink.domain.org.rules.DataScopeResolver;
import io.tracelink.domain.task.model.TaskQuery;
import io.tracelink.domain.task.model.TaskScope;
import io.tracelink.domain.task.model.TaskType;
import io.tracelink.domain.task.repository.TaskRepository;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 六类只读工具的确定性路由（DESIGN 4.8）。模型不产出 SQL，也不给写接口；
 * 关键词只决定「查哪一类」，真正的过滤条件由应用服务按调用人数据范围注入。
 * db_query 是唯一让模型生成 SQL 的工具——但 schema 由应用端手动挑选、
 * 权限范围由应用端强制注入、SQL 经过正则安全拦截后用 JDBC 执行。
 * 越权的部门或人员输入一律裁剪为调用人范围，不报错，避免把范围边界当错误暴露。
 */
@Service
public class ChatToolService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int TASK_LIMIT = 10;
    private static final int EMPLOYEE_LIMIT = 5;
    private static final int ORG_LIMIT = 8;
    private static final int DB_QUERY_LIMIT = 50;

    /** 问题关键词 → 相关表名的意图映射；最多取 4 张表，避免撑爆模型 context */
    private static final Map<String, String[]> INTENT_TABLES = new LinkedHashMap<>();

    static {
        INTENT_TABLES.put("task", new String[]{
                "task",                    // 工单主表
                "employee",                // 主责/负责人/创建人
                "dept"                     // 部门
        });
        INTENT_TABLES.put("task_checkin", new String[]{
                "task_checkin", "task", "employee", "dept"
        });
        INTENT_TABLES.put("task_gate", new String[]{
                "task_gate_snapshot", "task", "employee"
        });
        INTENT_TABLES.put("task_action", new String[]{
                "task_action", "task", "employee"
        });
    }

    /** 拦截写操作与危险关键字（MySQL 方言） */
    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|REPLACE|LOAD_FILE|DUMPFILE|EXPLAIN|DESCRIBE|DES|GRANT|REVOKE|RENAME|REPAIR|OPTIMIZE|CHECK|ANALYZE|USE)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LIMIT_CLAUSE = Pattern.compile("\\bLIMIT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FENCE_SQL = Pattern.compile("```sql\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern FENCE_ANY = Pattern.compile("```\\s*\\n([\\s\\S]*?)```");

    private static final String DB_SYSTEM_PROMPT = """
            你是 MySQL 专家，负责把中文问题翻译成一条只读 SELECT。
            规则：
            1. 只输出 ```sql ... ``` 代码块，不要解释、不要多语；
            2. 只读 SELECT，严禁任何 INSERT/UPDATE/DELETE/DROP/ALTER/TRUNCATE/CREATE；
            3. 字段和表名一律用反引号；
            4. 关联 employee 表时用 employee.name 显示人名；
            5. 关联 dept 表时用 dept.name 显示部门名；
            6. 逾期判定：plan_end < NOW() 且 status NOT IN ('DONE','CANCELLED')；
            7. 主责人字段：task.primary_assignee_id → employee.id；负责人字段：owner_id；
            8. 工单状态枚举：DRAFT/PENDING/IN_PROGRESS/SUBMITTED/DONE/BLOCKED/CANCELLED；
            9. 工单类型枚举：CORRECTIVE/IMPROVEMENT/INSPECTION/INVESTIGATION/COORDINATION/OTHER/PLAN/QUALITY/PATROL；
            10. 所有 task 表都要加 deleted = 0 过滤；
            11. 必须加 LIMIT %d，除非你自己加了更小的；
            12. 逾期率公式：逾期率 = SUM(CASE WHEN 逾期判定成立 THEN 1 ELSE 0 END) / COUNT(*) * 100。
                这里 COUNT(*) 是**该分组的全部工单**（含已完成、未逾期、正常在办），
                不是只在"已逾期子集"上的 COUNT。因此先过滤逾期再 GROUP BY 时拿不到完整分母——
                此时要做 LEFT JOIN：先选出全部工单（不过滤逾期）做主查询，
                再用 CASE WHEN 计算逾期数做聚合，分母就是 COUNT(*)。
                绝对不要用 COUNT(*) OVER (PARTITION BY ...) 这类窗口函数当分母，
                它在 GROUP BY 之后只会等于当前分组 count，分子分母相同导致逾期率恒等于 100。
            13. 完成率公式：完成率 = SUM(CASE WHEN status = 'DONE' THEN 1 ELSE 0 END) / COUNT(*) * 100。
            """.stripIndent();

    private final TaskAppService taskAppService;
    private final StatsAppService statsAppService;
    private final TaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;
    private final DeptRepository deptRepository;
    private final ChatPort chatPort;
    private final DataSource dataSource;

    public ChatToolService(TaskAppService taskAppService,
                           StatsAppService statsAppService,
                           TaskRepository taskRepository,
                           EmployeeRepository employeeRepository,
                           DeptRepository deptRepository,
                           ChatPort chatPort,
                           DataSource dataSource) {
        this.taskAppService = taskAppService;
        this.statsAppService = statsAppService;
        this.taskRepository = taskRepository;
        this.employeeRepository = employeeRepository;
        this.deptRepository = deptRepository;
        this.chatPort = chatPort;
        this.dataSource = dataSource;
    }

    public List<ChatToolResult> run(AuthUser actor, ChatAskCommand command) {
        String question = command == null || command.question() == null ? "" : command.question();
        if (question.isBlank()) {
            return List.of();
        }
        TaskScope scope = DataScopeResolver.resolve(actor, deptRepository.findAll());
        List<ChatToolResult> results = new ArrayList<>();
        if (needsTask(question)) {
            results.add(taskTool(actor, question, scope));
        }
        if (needsStats(question)) {
            results.add(statsTool(actor, question, scope));
        }
        if (needsEmployee(question)) {
            results.add(employeeTool(actor, question, scope));
        }
        if (needsOrg(question)) {
            results.add(orgTool(actor, question, scope));
        }
        // 维度聚合：当问题需要按主责/部门/状态分组时触发——现有 stats_query 只给整体 KPI，
        // task_query 只给明细（硬 limit=10），都覆盖不了"谁最多"这类意图
        if (needsSql(question)) {
            results.add(dbQuery(actor, question, scope));
        }
        return results;
    }

    // ---------------------------------------------------------------- 工单

    private ChatToolResult taskTool(AuthUser actor, String question, TaskScope scope) {
        long start = System.currentTimeMillis();
        boolean clamped = false;
        List<Long> allowed = deptScope(actor, scope);
        Long requested = deptInQuestion(question);
        Long deptId = null;
        if (requested != null) {
            if (allowed == null || allowed.contains(requested)) {
                deptId = requested;
            } else {
                clamped = true;
            }
        }
        boolean overdue = question.contains("逾期") || question.contains("超期");
        String status = null;
        if (question.contains("未验收") || question.contains("待验收") || question.contains("没验收")) {
            status = "SUBMITTED";
        } else if (question.contains("待接单")) {
            status = "PENDING";
        } else if (question.contains("进行中")) {
            status = "IN_PROGRESS";
        } else if (question.contains("阻碍") || question.contains("阻塞")) {
            status = "BLOCKED";
        } else if (question.contains("已完成") || question.contains("验收完")) {
            status = "DONE";
        }
        TaskType type = typeInQuestion(question);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("deptId", deptId);
        args.put("status", status);
        args.put("type", type == null ? null : type.name());
        args.put("overdueOnly", overdue);
        args.put("clamped", clamped);
        args.put("limit", TASK_LIMIT);

        PageResult<TaskView> page = taskAppService.page(actor, null, status,
                type == null ? null : type.name(), deptId == null ? null : String.valueOf(deptId),
                null, null, false, overdue, false, 1, TASK_LIMIT);
        List<String> facts = page.records().stream()
                .map(t -> t.taskNo() + " " + t.title() + "（" + t.statusLabel() + "，期限 "
                        + (t.planEnd() == null ? "未定" : t.planEnd().toLocalDate().format(DAY))
                        + (t.primaryAssigneeName() == null ? "" : "，主责 " + t.primaryAssigneeName()) + "）")
                .toList();
        return new ChatToolResult("task_query", "查工单", args, (int) page.total(),
                System.currentTimeMillis() - start, facts);
    }

    // ---------------------------------------------------------------- 统计

    private ChatToolResult statsTool(AuthUser actor, String question, TaskScope scope) {
        long start = System.currentTimeMillis();
        LocalDate to = LocalDate.now();
        LocalDate from;
        if (question.contains("年")) {
            from = to.withDayOfYear(1);
        } else if (question.contains("月")) {
            from = to.withDayOfMonth(1);
        } else if (question.contains("周")) {
            from = to.minusDays(6);
        } else {
            from = to.minusDays(29);
        }
        boolean clamped = false;
        List<Long> allowed = deptScope(actor, scope);
        Long deptId = null;
        Long requested = deptInQuestion(question);
        if (requested != null) {
            if (allowed == null || allowed.contains(requested)) {
                deptId = requested;
            } else {
                clamped = true;
            }
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("from", from.format(DAY));
        args.put("to", to.format(DAY));
        args.put("clamped", clamped);
        // SELF 范围只返回本人的聚合数字，避免部门聚合把他人数据带出去
        if (scope.selfOnly()) {
            StatsPersonView personView = statsAppService.computePerson(from, to, actor.deptId(),
                    actor.employeeId());
            List<String> facts = new ArrayList<>();
            for (PersonStat person : personView.persons()) {
                facts.add("新建 " + person.created() + " 单");
                facts.add("应完成 " + person.due() + " 单，已完成 " + person.done() + " 单");
                facts.add("在办 " + person.open() + " 单，其中逾期 " + person.overdue() + " 单");
                facts.add("按时完成率：" + format(person.onTimeRate()) + "%");
                facts.add("结果留痕率：" + format(person.evidenceRate()) + "%");
            }
            args.put("employeeId", actor.employeeId());
            args.put("deptId", actor.deptId());
            return new ChatToolResult("stats_query", "查统计",
                    args, facts.size(), System.currentTimeMillis() - start, facts);
        }
        // 统计聚合只按单个部门取：调用人不是全域范围时，一律收回可见部门，避免空参数变成全公司口径
        Long statDept = allowed == null ? deptId : (deptId != null ? deptId : actor.deptId());
        StatsOverviewView view = statsAppService.computeOverview(from, to, statDept);
        List<String> facts = view.kpis().stream()
                .limit(6)
                .map(k -> k.label() + "：" + format(k.value()) + (k.unit() == null ? "" : k.unit()))
                .toList();
        args.put("deptId", statDept);
        return new ChatToolResult("stats_query", "查统计",
                args, view.kpis().size(), System.currentTimeMillis() - start, facts);
    }

    // ---------------------------------------------------------------- 人员

    private ChatToolResult employeeTool(AuthUser actor, String question, TaskScope scope) {
        long start = System.currentTimeMillis();
        boolean clamped = false;
        List<Long> allowed = deptScope(actor, scope);
        Long requested = deptInQuestion(question);
        Long deptId = null;
        if (requested != null) {
            if (allowed == null || allowed.contains(requested)) {
                deptId = requested;
            } else {
                clamped = true;
            }
        }
        Map<Long, String> deptNames = new LinkedHashMap<>();
        for (Dept dept : deptRepository.findAll()) {
            deptNames.put(dept.getId(), dept.getName());
        }
        Long deptFilter = deptId;
        List<Employee> visible = employeeRepository.findAll().stream()
                .filter(e -> e.getStatus() == null || e.getStatus() == 1)
                .filter(e -> scope.selfOnly() ? Objects.equals(actor.employeeId(), e.getId()) : true)
                .filter(e -> allowed == null || allowed.contains(e.getDeptId()))
                .filter(e -> deptFilter == null || Objects.equals(deptFilter, e.getDeptId()))
                .toList();
        List<Employee> named = visible.stream()
                .filter(e -> e.getName() != null && e.getName().length() >= 2 && question.contains(e.getName()))
                .toList();
        List<Employee> targets;
        if (!named.isEmpty()) {
            targets = named.stream().limit(EMPLOYEE_LIMIT).toList();
        } else {
            LocalDateTime now = LocalDateTime.now();
            targets = visible.stream()
                    .sorted(Comparator.comparingLong(e -> -loadOf(e.getId(), scope, now)))
                    .limit(EMPLOYEE_LIMIT)
                    .toList();
        }
        LocalDateTime now = LocalDateTime.now();
        List<String> facts = targets.stream()
                .map(e -> e.getName() + "（" + deptNames.getOrDefault(e.getDeptId(), "未分配部门") + "）在办 "
                        + loadOf(e.getId(), scope, now) + " 单")
                .toList();
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("deptId", deptId);
        args.put("names", named.stream().map(Employee::getName).toList());
        args.put("clamped", clamped);
        args.put("limit", EMPLOYEE_LIMIT);
        return new ChatToolResult("employee_query", "查人员",
                args, targets.size(), System.currentTimeMillis() - start, facts);
    }

    private long loadOf(Long employeeId, TaskScope scope, LocalDateTime now) {
        return taskRepository.count(new TaskQuery(null, null, null, null, null, employeeId,
                false, false, false, now, scope, 1, 1));
    }

    // ---------------------------------------------------------------- 组织

    /**
     * 部门组织数据：编制、在职、负责人、上下级与在办负载。
     * 问题点名部门时聚合该部门及其下级，否则取调用人可见范围内的部门。
     */
    private ChatToolResult orgTool(AuthUser actor, String question, TaskScope scope) {
        long start = System.currentTimeMillis();
        List<Dept> all = deptRepository.findAll();
        Map<Long, Dept> byId = new LinkedHashMap<>();
        for (Dept dept : all) {
            byId.put(dept.getId(), dept);
        }
        List<Long> allowed = deptScope(actor, scope);
        Long requested = deptInQuestion(question);
        boolean clamped = requested != null && allowed != null && !allowed.contains(requested);
        Long focusId = clamped ? null : requested;
        Set<Long> focus = new LinkedHashSet<>();
        if (focusId != null) {
            collectSubtree(focusId, all, focus);
        } else if (allowed != null) {
            focus.addAll(allowed);
        } else {
            for (Dept dept : all) {
                focus.add(dept.getId());
            }
        }
        Map<Long, Long> active = new LinkedHashMap<>();
        Map<Long, String> leaderNames = new LinkedHashMap<>();
        for (Employee employee : employeeRepository.findAll()) {
            leaderNames.put(employee.getId(), employee.getName());
            if (employee.getStatus() != null && employee.getStatus() != 1) {
                continue;
            }
            if (scope.selfOnly() && !Objects.equals(actor.employeeId(), employee.getId())) {
                continue;
            }
            if (employee.getDeptId() != null && focus.contains(employee.getDeptId())) {
                active.merge(employee.getDeptId(), 1L, Long::sum);
            }
        }
        List<Dept> rows = focus.stream().map(byId::get).filter(Objects::nonNull)
                .sorted(Comparator.comparingInt((Dept dept) -> dept.getLevel() == null ? 0 : dept.getLevel())
                        .thenComparingInt(dept -> dept.getSortNo() == null ? 0 : dept.getSortNo()))
                .limit(ORG_LIMIT)
                .toList();
        List<String> facts = new ArrayList<>();
        for (Dept dept : rows) {
            long open = taskAppService.page(actor, null, null, null, String.valueOf(dept.getId()),
                    null, null, false, false, false, 1, 1).total();
            long children = all.stream().filter(d -> Objects.equals(d.getParentId(), dept.getId())).count();
            Dept parent = dept.getParentId() == null ? null : byId.get(dept.getParentId());
            facts.add(dept.getName()
                    + "（上级 " + (parent == null ? "无" : parent.getName())
                    + "，编制 " + (dept.getHeadcount() == null ? "未设" : dept.getHeadcount()) + " 人"
                    + "，在职 " + active.getOrDefault(dept.getId(), 0L) + " 人"
                    + "，负责人 " + leaderNames.getOrDefault(dept.getLeaderEmployeeId(), "未设")
                    + "，在办 " + open + " 单"
                    + "，下级 " + children + " 个）");
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("deptId", focusId);
        args.put("depts", focus.size());
        args.put("clamped", clamped);
        args.put("limit", ORG_LIMIT);
        return new ChatToolResult("org_query", "查组织", args, rows.size(),
                System.currentTimeMillis() - start, facts);
    }

    private void collectSubtree(Long rootId, List<Dept> all, Set<Long> target) {
        if (!target.add(rootId)) {
            return;
        }
        for (Dept dept : all) {
            if (Objects.equals(dept.getParentId(), rootId)) {
                collectSubtree(dept.getId(), all, target);
            }
        }
    }

    // ---------------------------------------------------------------- 路由

    private boolean needsTask(String question) {
        return containsAny(question, "工单", "单子", "任务", "待办", "验收", "派工", "进度",
                "期限", "逾期", "超期", "阻碍", "阻塞", "几张", "多少单");
    }

    private boolean needsStats(String question) {
        return containsAny(question, "统计", "完成率", "按时率", "逾期率", "逾期", "趋势", "指标",
                "对比", "效率", "多少", "为什么", "原因", "积压", "瓶颈");
    }

    private boolean needsEmployee(String question) {
        return containsAny(question, "谁", "人员", "人手", "负载", "负荷", "在办", "忙", "执行人", "负责人");
    }

    private boolean needsOrg(String question) {
        return deptInQuestion(question) != null
                || containsAny(question, "部门", "组织", "架构", "编制", "团队", "主管", "岗位",
                "上级", "下级", "多少人", "几个人");
    }

    /**
     * 需要维度聚合的意图：按主责/部门/状态分组、Top N 排行、分布统计。
     * 与 needsStats 的区别：needsStats 要的是整体 KPI 数字；needsSql 要的是分组明细。
     */
    private boolean needsSql(String question) {
        return containsAny(question, "谁最多", "谁最少", "最多的", "最少的",
                "排行", "排名", "top", "TOP", "第一", "前几", "前5", "前10",
                "分组", "分布", "各部门的", "各主责", "每个部门", "每个主责",
                "按主责", "按执行人", "按负责人", "按部门", "按状态",
                "逾期的人", "逾期的部门", "逾期排行", "积压最多");
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 工具可用的部门范围：ALL 为 null（不收窄）；DEPT/DEPT_TREE 取换算后的部门列表；
     * SELF 只允许本人部门，避免空部门参数被当成全公司口径。
     */
    private List<Long> deptScope(AuthUser actor, TaskScope scope) {
        if (scope.deptIds() != null) {
            return scope.deptIds();
        }
        if (scope.selfOnly()) {
            return actor.deptId() == null ? List.of() : List.of(actor.deptId());
        }
        return null;
    }

    /** 从问题里识别部门名，供工单、统计、人员与知识检索统一收窄范围 */
    public Long deptInQuestion(String question) {
        Long matched = null;
        String longest = "";
        for (Dept dept : deptRepository.findAll()) {
            String name = dept.getName();
            if (name != null && name.length() >= 2 && question.contains(name) && name.length() > longest.length()) {
                longest = name;
                matched = dept.getId();
            }
        }
        return matched;
    }

    private TaskType typeInQuestion(String question) {
        for (TaskType type : TaskType.values()) {
            if (question.contains(type.label())) {
                return type;
            }
        }
        return null;
    }

    private String format(double value) {
        return Math.abs(value - Math.round(value)) < 0.01
                ? String.valueOf((long) Math.round(value)) : String.format("%.2f", value);
    }

    // ---------------------------------------------------------------- db_query（维度聚合）

    /**
     * 让模型看精简 schema → 生成只读 SELECT → JDBC 执行 → 转成聚合事实。
     * 权限范围：通过 user prompt 显式告诉模型 actor 的可见部门与员工 id，
     * 但真正的 WHERE 注入在模型生成后由应用端补位（更安全，模型可能漏加）。
     * 如果模型不可用或生成 SQL 被拦截，返回一条错误提示事实。
     */
    private ChatToolResult dbQuery(AuthUser actor, String question, TaskScope scope) {
        long start = System.currentTimeMillis();
        Map<String, Object> args = new LinkedHashMap<>();
        // 1. 挑表（精简 schema，避免撑爆模型 context）
        String[] tables = pickTables(question);
        String schema = schemaFor(tables);
        args.put("tables", Arrays.asList(tables));

        // 2. 构造权限说明（给模型看的范围提示）
        String scopeHint = scopeHint(actor, scope);
        args.put("scope", scopeHint);

        // 3. 调模型生成 SQL（同步收完整内容，ChatPort.stream 是流式回调，这里用 StringBuilder 收）
        String sql = null;
        String genErr = null;
        if (chatPort == null || !chatPort.available()) {
            genErr = "模型不可用，无法生成维度聚合 SQL";
        } else {
            String system = String.format(DB_SYSTEM_PROMPT, DB_QUERY_LIMIT);
            String userPrompt = "--- 数据库 schema ---\n" + schema
                    + "\n\n--- 调用人权限范围 ---\n" + scopeHint
                    + "\n\n--- 问题 ---\n" + question;
            StringBuilder buffer = new StringBuilder();
            try {
                chatPort.stream(system, userPrompt, buffer::append);
                sql = extractSql(buffer.toString());
            } catch (RuntimeException e) {
                sql = null;
                genErr = "模型生成失败：" + e.getMessage();
            }
        }

        // 4. 安全检查
        List<String> facts = new ArrayList<>();
        if (genErr != null || sql == null || sql.isBlank()) {
            facts.add("db_query 未能生成 SQL：" + genErr);
            return new ChatToolResult("db_query", "维度聚合", args, 0,
                    System.currentTimeMillis() - start, facts);
        }
        Matcher forbiddenMatcher = FORBIDDEN.matcher(sql);
        if (forbiddenMatcher.find()) {
            facts.add("db_query 拒绝执行（发现危险关键字：" + forbiddenMatcher.group(1) + "）");
            return new ChatToolResult("db_query", "维度聚合", args, 0,
                    System.currentTimeMillis() - start, facts);
        }

        // 5. 确保有 LIMIT（避免模型忘加拉全表）
        if (!LIMIT_CLAUSE.matcher(sql).find()) {
            sql = sql.trim().replaceAll(";\\s*$", "") + " LIMIT " + DB_QUERY_LIMIT;
        }
        args.put("sql", sql);

        // 6. JDBC 执行
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            List<String> headers = new ArrayList<>();
            for (int i = 1; i <= cols; i++) {
                headers.add(meta.getColumnLabel(i));
            }
            while (rs.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 0; i < headers.size(); i++) {
                    if (i > 0) row.append("，");
                    Object v = rs.getObject(i + 1);
                    row.append(headers.get(i)).append(" ").append(formatValue(v));
                }
                facts.add(row.toString());
            }
        } catch (SQLException e) {
            facts.add("db_query 执行失败：" + e.getMessage());
        }

        return new ChatToolResult("db_query", "维度聚合", args, facts.size(),
                System.currentTimeMillis() - start, facts);
    }

    private String[] pickTables(String question) {
        // 按关键词命中挑一组表；默认 task+employee+dept
        Set<String> picked = new LinkedHashSet<>();
        picked.addAll(Arrays.asList(INTENT_TABLES.get("task")));
        if (containsAny(question, "追踪", "记录", "检查", "反馈", "进度更新")) {
            picked.addAll(Arrays.asList(INTENT_TABLES.get("task_checkin")));
        }
        if (containsAny(question, "完成条件", "缺口", "验收条件", "gate")) {
            picked.addAll(Arrays.asList(INTENT_TABLES.get("task_gate")));
        }
        if (containsAny(question, "措施", "动作", "整改措施", "action")) {
            picked.addAll(Arrays.asList(INTENT_TABLES.get("task_action")));
        }
        return picked.toArray(new String[0]);
    }

    private String schemaFor(String[] tables) {
        StringBuilder sb = new StringBuilder();
        try (Connection conn = dataSource.getConnection()) {
            for (String t : tables) {
                sb.append("\n### ").append(t).append("\n```\n");
                try (PreparedStatement ps = conn.prepareStatement("SHOW CREATE TABLE `" + t + "`");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        sb.append(rs.getString(2));
                    }
                }
                sb.append("\n```\n");
            }
        } catch (SQLException e) {
            sb.append("ERROR: ").append(e.getMessage());
        }
        return sb.toString();
    }

    private String extractSql(String answer) {
        Matcher m = FENCE_SQL.matcher(answer);
        if (m.find()) return m.group(1).trim();
        Matcher m2 = FENCE_ANY.matcher(answer);
        if (m2.find()) return m2.group(1).trim();
        return answer.trim();
    }

    private String scopeHint(AuthUser actor, TaskScope scope) {
        List<Long> allowed = deptScope(actor, scope);
        StringBuilder sb = new StringBuilder();
        sb.append("调用人：").append(actor.name()).append("（employee_id=")
                .append(actor.employeeId()).append(", dept_id=").append(actor.deptId()).append("）\n");
        if (allowed == null) {
            sb.append("可见范围：全域（全部部门）");
        } else if (scope.selfOnly()) {
            sb.append("可见范围：仅本人（primary_assignee_id=").append(actor.employeeId()).append("）");
        } else {
            sb.append("可见部门 ID：").append(allowed).append("\n");
            sb.append("生成 SQL 时应在 WHERE 子句里把 dept_id 限制在这些值内。");
        }
        return sb.toString();
    }

    private String formatValue(Object v) {
        if (v == null) return "未设";
        if (v instanceof java.time.LocalDateTime ldt) return ldt.toLocalDate().format(DAY);
        if (v instanceof java.time.LocalDate ld) return ld.format(DAY);
        if (v instanceof java.math.BigDecimal bd) {
            double d = bd.doubleValue();
            return Math.abs(d - Math.round(d)) < 0.01
                    ? String.valueOf((long) Math.round(d)) : String.format("%.2f", d);
        }
        return v.toString();
    }
}
