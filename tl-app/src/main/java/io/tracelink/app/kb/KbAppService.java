package io.tracelink.app.kb;

import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.audit.model.AuditLog;
import io.tracelink.domain.audit.repository.AuditLogRepository;
import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.kb.model.KbChunk;
import io.tracelink.domain.kb.model.KbDocument;
import io.tracelink.domain.kb.model.KbDomain;
import io.tracelink.domain.kb.model.KbScope;
import io.tracelink.domain.kb.model.KbSyncStatus;
import io.tracelink.domain.kb.model.KbVisibility;
import io.tracelink.domain.kb.model.KnowledgeHit;
import io.tracelink.domain.kb.parse.DocumentParser;
import io.tracelink.domain.kb.parse.ParsedDocument;
import io.tracelink.domain.kb.remote.KbRemotePort;
import io.tracelink.domain.kb.repository.KbChunkRepository;
import io.tracelink.domain.kb.repository.KbDocumentRepository;
import io.tracelink.domain.kb.retriever.KnowledgeRetriever;
import io.tracelink.domain.org.model.Dept;
import io.tracelink.domain.org.model.Employee;
import io.tracelink.domain.org.repository.DeptRepository;
import io.tracelink.domain.org.repository.EmployeeRepository;
import io.tracelink.domain.storage.FileStorage;
import io.tracelink.domain.task.model.Task;
import io.tracelink.domain.task.model.TaskAction;
import io.tracelink.domain.task.model.TaskIssue;
import io.tracelink.domain.task.repository.TaskActionRepository;
import io.tracelink.domain.task.repository.TaskIssueRepository;
import io.tracelink.domain.task.repository.TaskRepository;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 知识库应用服务：上传与解析状态、切片快照、历史问题措施沉淀、带范围过滤的检索、切片原文回溯。
 * 解析与检索优先交给 RAGFlow；RAGFlow 未配置时按 DESIGN 4.7 的降级路径走本地解析与 ngram 全文，
 * 两条路径都写同一张 kb_chunk 快照，保证引用可回溯、页面只认一种读法。
 */
@Service
public class KbAppService implements DisposableBean {

    private static final String ROLE_ADMIN = "SYS_ADMIN";
    private static final String BIZ_KB = "KB";
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_LIST = 200;
    private static final int MAX_TITLE = 200;
    private static final int MAX_QUESTION = 500;
    private static final int EXCERPT_LENGTH = 120;

    private final KbDocumentRepository documentRepository;
    private final KbChunkRepository chunkRepository;
    private final KnowledgeRetriever retriever;
    private final KbRemotePort remote;
    private final DocumentParser parser;
    private final FileStorage fileStorage;
    private final DeptRepository deptRepository;
    private final EmployeeRepository employeeRepository;
    private final TaskRepository taskRepository;
    private final TaskIssueRepository issueRepository;
    private final TaskActionRepository actionRepository;
    private final AuditLogRepository auditLogRepository;
    private final KbScopeResolver scopeResolver;
    private final boolean ragFlowEnabled;
    private final String datasetId;
    private final double threshold;
    private final int lookbackDays;
    private final int maxFileMb;
    private final ExecutorService ingestExecutor;

    public KbAppService(KbDocumentRepository documentRepository,
                        KbChunkRepository chunkRepository,
                        KnowledgeRetriever retriever,
                        KbRemotePort remote,
                        DocumentParser parser,
                        FileStorage fileStorage,
                        DeptRepository deptRepository,
                        EmployeeRepository employeeRepository,
                        TaskRepository taskRepository,
                        TaskIssueRepository issueRepository,
                        TaskActionRepository actionRepository,
                        AuditLogRepository auditLogRepository,
                        KbScopeResolver scopeResolver,
                        @Value("${tracelink.ai.ragflow.enabled:false}") boolean ragFlowEnabled,
                        @Value("${tracelink.ai.ragflow.dataset-id:default}") String datasetId,
                        @Value("${tracelink.kb.search.threshold:0.2}") double threshold,
                        @Value("${tracelink.kb.history.lookback-days:365}") int lookbackDays,
                        @Value("${tracelink.kb.max-file-size-mb:50}") int maxFileMb) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.retriever = retriever;
        this.remote = remote;
        this.parser = parser;
        this.fileStorage = fileStorage;
        this.deptRepository = deptRepository;
        this.employeeRepository = employeeRepository;
        this.taskRepository = taskRepository;
        this.issueRepository = issueRepository;
        this.actionRepository = actionRepository;
        this.auditLogRepository = auditLogRepository;
        this.scopeResolver = scopeResolver;
        this.ragFlowEnabled = ragFlowEnabled;
        this.datasetId = datasetId;
        this.threshold = Math.max(0.0, Math.min(1.0, threshold));
        this.lookbackDays = Math.max(1, lookbackDays);
        this.maxFileMb = Math.max(1, maxFileMb);
        this.ingestExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "kb-ingest");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void destroy() {
        ingestExecutor.shutdownNow();
    }

    // ---------------------------------------------------------------- 文档入库与同步

    public KbDocumentView upload(AuthUser actor, KbUploadCommand command, InputStream content) {
        requireAdmin(actor, "上传知识文档");
        String fileName = trim(command.fileName());
        if (fileName.isEmpty()) {
            throw BizException.invalid("请选择要上传的文件");
        }
        if (!supported(fileName)) {
            throw BizException.invalid("暂不支持该文件类型，可上传 pdf、docx、md、txt");
        }
        FileStorage.StoredFile stored = fileStorage.store("kb", fileName, content);
        if (stored.size() > (long) maxFileMb * 1024L * 1024L) {
            throw BizException.invalid("文件超过 " + maxFileMb + " MB 上限，请拆分后重试");
        }
        KbDocument existing = documentRepository.findByHash(stored.sha256());
        if (existing != null) {
            return toView(existing, deptMap());
        }
        Long deptId = command.deptId() == null ? actor.deptId() : command.deptId();
        Dept dept = deptId == null ? null : deptRepository.findById(deptId);
        if (deptId != null && dept == null) {
            throw BizException.invalid("所属部门不存在");
        }
        KbDocument document = new KbDocument();
        document.title = titleOf(command.title(), fileName);
        document.domain = KbDomain.parse(command.domain());
        document.deptId = dept == null ? null : dept.getId();
        document.deptCode = dept == null ? null : dept.getCode();
        document.visibility = dept == null ? KbVisibility.ALL : KbVisibility.parse(command.visibility());
        document.fileKey = stored.key();
        document.fileHash = stored.sha256();
        document.sourceType = blank(command.sourceType()) ? "UPLOAD" : command.sourceType().trim();
        document.datasetId = datasetId;
        document.chunkCnt = 0;
        document.syncStatus = KbSyncStatus.PENDING;
        document.createdBy = actor.employeeId();
        document.deleted = false;
        documentRepository.insert(document);
        audit(actor, "KB_UPLOAD", document.id, null,
                "{\"title\":" + quote(document.title) + ",\"hash\":\"" + stored.sha256() + "\"}");
        KbDocumentView view = toView(document, deptMap());
        Long docId = document.id;
        ingestExecutor.submit(() -> ingest(docId));
        return view;
    }

    public List<KbDocumentView> list(AuthUser actor, String domain, Long deptId, String status, String keyword) {
        KbDomain kbDomain = parseDomain(domain);
        KbSyncStatus syncStatus = blank(status) ? null : KbSyncStatus.parse(status);
        KbScope scope = scopeResolver.resolve(actor);
        Map<Long, Dept> depts = deptMap();
        return documentRepository.list(kbDomain, deptId, syncStatus, keyword, MAX_LIST).stream()
                .filter(document -> readable(document, scope))
                .map(document -> toView(document, depts))
                .toList();
    }

    public KbSyncView sync(AuthUser actor, Long documentId) {
        requireAdmin(actor, "查看解析状态");
        KbDocument document = requireDocument(documentId);
        if (remoteActive() && document.externalDocId != null && !document.externalDocId.isBlank()
                && KbSyncStatus.PARSING == document.syncStatus) {
            try {
                pollRemote(document);
            } catch (BizException e) {
                documentRepository.updateSync(document.id, KbSyncStatus.PARSING,
                        limit("RAGFlow 暂不可达：" + messageOf(e), 500), null, null, null);
            }
            document = requireDocument(documentId);
        }
        return toSyncView(document);
    }

    public KbSyncView reparse(AuthUser actor, Long documentId) {
        requireAdmin(actor, "重新解析文档");
        KbDocument document = requireDocument(documentId);
        if (KbSyncStatus.PARSING == document.syncStatus) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, "文档正在解析，请稍后再试");
        }
        documentRepository.updateSync(document.id, KbSyncStatus.PARSING,
                remoteActive() ? "已提交 RAGFlow 解析" : "本地解析中", null, null, null);
        Long docId = document.id;
        ingestExecutor.submit(() -> ingest(docId));
        String before = document.syncStatus == null ? "null" : quote(document.syncStatus.name());
        audit(actor, "KB_RESYNC", document.id, "{\"status\":" + before + "}",
                "{\"status\":" + quote(KbSyncStatus.PARSING.name()) + "}");
        return toSyncView(requireDocument(documentId));
    }

    // ---------------------------------------------------------------- 历史问题与措施沉淀

    public KbHistoryResult historySync(AuthUser actor, Long deptId, int limit) {
        requireAdmin(actor, "沉淀历史问题与措施");
        int max = clamp(limit, 1, 300);
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(lookbackDays);
        List<Task> tasks = taskRepository.findDoneBetween(from, to, deptId);
        if (tasks.size() > max) {
            tasks = tasks.subList(tasks.size() - max, tasks.size());
        }
        if (tasks.isEmpty()) {
            return new KbHistoryResult(0, 0, 0, List.of());
        }
        List<Long> taskIds = tasks.stream().map(task -> task.id).toList();
        Map<Long, List<TaskIssue>> issues = issueRepository.listByTaskIds(taskIds).stream()
                .collect(Collectors.groupingBy(issue -> issue.taskId));
        Map<Long, List<TaskAction>> actions = actionRepository.listByTaskIds(taskIds).stream()
                .collect(Collectors.groupingBy(action -> action.taskId));
        Map<Long, Dept> depts = deptMap();
        Map<Long, String> names = employeeNames();

        int created = 0;
        int skipped = 0;
        List<Long> documentIds = new ArrayList<>();
        for (Task task : tasks) {
            String markdown = markdown(task, issues.getOrDefault(task.id, List.of()),
                    actions.getOrDefault(task.id, List.of()), depts, names);
            FileStorage.StoredFile stored = fileStorage.store("kb", "历史问题-" + task.taskNo + ".md",
                    new ByteArrayInputStream(markdown.getBytes(StandardCharsets.UTF_8)));
            if (documentRepository.findByHash(stored.sha256()) != null) {
                skipped++;
                continue;
            }
            KbDocument document = new KbDocument();
            document.title = limit("历史问题与措施：" + task.title, MAX_TITLE);
            document.domain = KbDomain.HISTORY;
            document.deptId = task.deptId;
            Dept dept = task.deptId == null ? null : depts.get(task.deptId);
            document.deptCode = dept == null ? null : dept.getCode();
            document.visibility = dept == null ? KbVisibility.ALL : KbVisibility.DEPT;
            document.fileKey = stored.key();
            document.fileHash = stored.sha256();
            document.sourceType = "TASK_PAIR";
            document.sourceRef = task.taskNo;
            document.datasetId = datasetId;
            document.chunkCnt = 0;
            document.syncStatus = KbSyncStatus.PENDING;
            document.createdBy = actor.employeeId();
            document.deleted = false;
            documentRepository.insert(document);
            if (remoteActive()) {
                Long docId = document.id;
                ingestExecutor.submit(() -> ingest(docId));
            } else {
                ingestLocalQuietly(document);
            }
            documentIds.add(document.id);
            created++;
        }
        audit(actor, "KB_HISTORY_SYNC", 0L, null,
                "{\"scanned\":" + tasks.size() + ",\"created\":" + created + ",\"skipped\":" + skipped + "}");
        return new KbHistoryResult(tasks.size(), created, skipped, documentIds);
    }

    // ---------------------------------------------------------------- 检索与切片回溯

    public KbSearchView search(AuthUser actor, String question, String domain, Long deptId, Integer topK) {
        String trimmed = trim(question);
        if (trimmed.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入要检索的问题");
        }
        if (trimmed.length() > MAX_QUESTION) {
            throw new BizException(ErrorCode.QUESTION_INVALID, "问题不超过 " + MAX_QUESTION + " 字");
        }
        KbDomain kbDomain = parseDomain(domain);
        KbScope scope = scopeResolver.resolve(actor);
        String retrieverName = retriever.name();
        boolean degraded = !"RAGFLOW".equalsIgnoreCase(retrieverName);
        if (deptId != null && !scope.all() && !scope.deptIds().contains(deptId)) {
            // 范围外的部门直接零命中，不报错也不回显任何文档标题
            return new KbSearchView(trimmed, retrieverName, degraded, 0, List.of());
        }
        int k = clamp(topK == null ? 6 : topK, 1, 20);
        List<KnowledgeHit> hits = retriever.retrieve(trimmed, scope, kbDomain, k, threshold);
        Map<Long, Dept> depts = deptMap();
        List<KbSearchView.Hit> rows = new ArrayList<>();
        for (KnowledgeHit hit : hits) {
            if (!readable(hit, scope)) {
                continue;
            }
            Dept dept = hit.deptId() == null ? null : depts.get(hit.deptId());
            String content = hit.content() == null ? "" : hit.content();
            rows.add(new KbSearchView.Hit(hit.docId(), hit.docTitle(),
                    hit.domain() == null ? null : hit.domain().name(),
                    hit.domain() == null ? null : hit.domain().label(),
                    hit.deptId(), dept == null ? null : dept.getName(), hit.seq(), hit.chunkId(),
                    content, excerpt(content), hit.pageNo(), hit.similarity(), hit.retriever()));
        }
        return new KbSearchView(trimmed, retrieverName, degraded, rows.size(), rows);
    }

    public KbChunkView chunk(AuthUser actor, Long chunkId) {
        KbChunk chunk = chunkRepository.findById(chunkId);
        if (chunk == null) {
            throw BizException.notFound("切片不存在");
        }
        KbDocument document = documentRepository.findById(chunk.docId);
        if (document == null) {
            throw BizException.notFound("该切片所属文档已删除");
        }
        if (!readable(document, scopeResolver.resolve(actor))) {
            throw new BizException(ErrorCode.OUT_OF_SCOPE, "该文档不在你的数据范围内");
        }
        return new KbChunkView(chunk.id, document.id, document.title, chunk.seq, chunk.pageNo,
                chunk.content, Boolean.TRUE.equals(chunk.superseded), chunk.createdAt);
    }

    // ---------------------------------------------------------------- 解析流水线

    private void ingest(Long documentId) {
        KbDocument document = documentRepository.findById(documentId);
        if (document == null) {
            return;
        }
        try {
            if (remoteActive()) {
                documentRepository.updateSync(documentId, KbSyncStatus.PARSING,
                        "已提交 RAGFlow 解析", null, null, null);
                submitRemote(document);
            } else {
                documentRepository.updateSync(documentId, KbSyncStatus.PARSING,
                        "本地解析中", null, null, null);
                ingestLocal(document);
            }
        } catch (RuntimeException e) {
            documentRepository.updateSync(documentId, KbSyncStatus.FAILED,
                    limit(messageOf(e), 500), null, null, LocalDateTime.now());
        }
    }

    private void ingestLocalQuietly(KbDocument document) {
        try {
            documentRepository.updateSync(document.id, KbSyncStatus.PARSING, "本地解析中", null, null, null);
            ingestLocal(document);
        } catch (RuntimeException e) {
            documentRepository.updateSync(document.id, KbSyncStatus.FAILED,
                    limit(messageOf(e), 500), null, null, LocalDateTime.now());
        }
    }

    private void ingestLocal(KbDocument document) {
        Path file = fileStorage.resolve(document.fileKey);
        ParsedDocument parsed = parser.parse(file, file.getFileName().toString());
        if (parsed.chunks().isEmpty()) {
            throw new BizException(ErrorCode.RULE_UNSATISFIED,
                    parsed.note() == null ? "未提取到文本，请确认文件不是扫描件或空文档" : parsed.note());
        }
        int count = saveLocalChunks(document, parsed.chunks());
        documentRepository.updateSync(document.id, KbSyncStatus.READY, parsed.note(), count, null,
                LocalDateTime.now());
    }

    private int saveLocalChunks(KbDocument document, List<ParsedDocument.ParsedChunk> chunks) {
        List<KbChunk> old = chunkRepository.listByDoc(document.id, false);
        List<KbChunk> fresh = new ArrayList<>();
        String batch = "L-" + document.id + "-" + System.nanoTime();
        int seq = 1;
        for (ParsedDocument.ParsedChunk piece : chunks) {
            fresh.add(chunk(document, seq, batch + "-" + seq, piece.content(), piece.pageNo()));
            seq++;
        }
        chunkRepository.insertBatch(fresh);
        supersede(old);
        return fresh.size();
    }

    private int saveRemoteChunks(KbDocument document, List<KbRemotePort.RemoteChunk> chunks) {
        List<KbChunk> old = chunkRepository.listByDoc(document.id, false);
        Map<String, KbChunk> existing = new HashMap<>();
        for (KbChunk prior : old) {
            if (!blank(prior.externalChunkId)) {
                existing.putIfAbsent(prior.externalChunkId, prior);
            }
        }
        String batch = "R-" + document.id + "-" + System.nanoTime();
        List<KbChunk> fresh = new ArrayList<>();
        List<Long> reused = new ArrayList<>();
        int seq = 1;
        for (KbRemotePort.RemoteChunk remoteChunk : chunks) {
            String externalId = blank(remoteChunk.id()) ? batch + "-" + seq : remoteChunk.id();
            KbChunk prior = existing.get(externalId);
            if (prior != null) {
                // RAGFlow 重新解析后沿用同一外部切片标识时直接复用原快照，避免唯一键冲突
                reused.add(prior.id);
            } else {
                fresh.add(chunk(document, seq, externalId, remoteChunk.content(), remoteChunk.pageNo()));
            }
            seq++;
        }
        chunkRepository.insertBatch(fresh);
        List<Long> stale = old.stream().map(item -> item.id)
                .filter(id -> !reused.contains(id)).toList();
        chunkRepository.supersedeByIds(stale);
        return reused.size() + fresh.size();
    }

    private KbChunk chunk(KbDocument document, int seq, String externalChunkId, String content, Integer pageNo) {
        KbChunk chunk = new KbChunk();
        chunk.docId = document.id;
        chunk.seq = seq;
        chunk.externalChunkId = externalChunkId;
        chunk.content = content == null ? "" : content;
        chunk.pageNo = pageNo;
        chunk.tokenCnt = chunk.content.length();
        chunk.meta = meta(document);
        chunk.superseded = false;
        return chunk;
    }

    /** 新片写成功后再让旧片失效，避免新片写失败时旧片已经不可检索 */
    private void supersede(List<KbChunk> old) {
        if (old.isEmpty()) {
            return;
        }
        chunkRepository.supersedeByIds(old.stream().map(chunk -> chunk.id).toList());
    }

    private void submitRemote(KbDocument document) {
        String externalId = document.externalDocId;
        if (blank(externalId)) {
            Path file = fileStorage.resolve(document.fileKey);
            try (InputStream in = Files.newInputStream(file)) {
                externalId = remote.uploadDocument(datasetId, file.getFileName().toString(), in, document.title);
            } catch (IOException e) {
                throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "原件读取失败：" + e.getMessage());
            }
            documentRepository.updateSync(document.id, KbSyncStatus.PARSING, "已上传 RAGFlow，等待解析",
                    null, externalId, null);
        }
        remote.triggerParse(datasetId, externalId);
        documentRepository.updateSync(document.id, KbSyncStatus.PARSING, "已提交 RAGFlow 解析",
                null, externalId, null);
    }

    private void pollRemote(KbDocument document) {
        KbRemotePort.ParseState state = remote.status(datasetId, document.externalDocId);
        if (state.failed()) {
            documentRepository.updateSync(document.id, KbSyncStatus.FAILED,
                    limit(state.message() == null ? "RAGFlow 解析失败" : state.message(), 500),
                    null, null, LocalDateTime.now());
            return;
        }
        if (!state.done()) {
            String progress = state.message() == null || state.message().isBlank()
                    ? "解析中" : state.message();
            documentRepository.updateSync(document.id, KbSyncStatus.PARSING, limit(progress, 500),
                    null, null, null);
            return;
        }
        List<KbRemotePort.RemoteChunk> chunks = remote.chunks(datasetId, document.externalDocId);
        if (chunks.isEmpty()) {
            documentRepository.updateSync(document.id, KbSyncStatus.FAILED,
                    "RAGFlow 解析完成但未返回切片，请确认文档内容", null, null, LocalDateTime.now());
            return;
        }
        int count = saveRemoteChunks(document, chunks);
        documentRepository.updateSync(document.id, KbSyncStatus.READY, "RAGFlow 解析完成", count,
                null, LocalDateTime.now());
    }

    // ---------------------------------------------------------------- 范围与视图

    private boolean remoteActive() {
        return ragFlowEnabled && remote.configured();
    }

    private boolean readable(KbDocument document, KbScope scope) {
        if (scope.all() || document.visibility == null || KbVisibility.ALL == document.visibility) {
            return true;
        }
        return document.deptId != null && scope.deptIds().contains(document.deptId);
    }

    private boolean readable(KnowledgeHit hit, KbScope scope) {
        if (scope.all() || hit.visibility() == null || KbVisibility.ALL == hit.visibility()) {
            return true;
        }
        return hit.deptId() != null && scope.deptIds().contains(hit.deptId());
    }

    private KbDocumentView toView(KbDocument document, Map<Long, Dept> depts) {
        Dept dept = document.deptId == null ? null : depts.get(document.deptId);
        return new KbDocumentView(document.id, document.title,
                document.domain == null ? null : document.domain.name(),
                document.domain == null ? null : document.domain.label(),
                document.deptId, dept == null ? null : dept.getName(), document.deptCode,
                document.visibility == null ? null : document.visibility.name(),
                document.visibility == null ? null : document.visibility.label(),
                document.sourceType, document.sourceRef, document.chunkCnt,
                document.syncStatus == null ? null : document.syncStatus.name(),
                document.syncStatus == null ? null : document.syncStatus.label(),
                document.syncMsg, document.syncedAt, document.createdAt);
    }

    private KbSyncView toSyncView(KbDocument document) {
        return new KbSyncView(document.id,
                document.syncStatus == null ? null : document.syncStatus.name(),
                document.syncStatus == null ? null : document.syncStatus.label(),
                document.chunkCnt, document.syncMsg, document.syncedAt, retriever.name());
    }

    private KbDocument requireDocument(Long documentId) {
        KbDocument document = documentId == null ? null : documentRepository.findById(documentId);
        if (document == null) {
            throw BizException.notFound("知识文档不存在或已删除");
        }
        return document;
    }

    private Map<Long, Dept> deptMap() {
        return deptRepository.findAll().stream()
                .collect(Collectors.toMap(Dept::getId, dept -> dept, (a, b) -> a, LinkedHashMap::new));
    }

    private Map<Long, String> employeeNames() {
        Map<Long, String> names = new HashMap<>();
        for (Employee employee : employeeRepository.findAll()) {
            names.put(employee.getId(), employee.getName());
        }
        return names;
    }

    private void requireAdmin(AuthUser actor, String action) {
        if (actor == null || actor.roles() == null || !actor.roles().contains(ROLE_ADMIN)) {
            throw new BizException(ErrorCode.NO_PERMISSION, "只有知识库管理员可以" + action);
        }
    }

    /** 审计不参与业务事务：写失败只丢失一条留痕，不影响上传、解析与检索的结果 */
    private void audit(AuthUser actor, String action, Long bizId, String before, String after) {
        try {
            AuditLog log = new AuditLog();
            log.actorId = actor == null ? null : actor.employeeId();
            log.action = action;
            log.bizType = BIZ_KB;
            log.bizId = bizId == null ? 0L : bizId;
            log.before = before;
            log.after = after;
            auditLogRepository.insert(log);
        } catch (RuntimeException ignored) {
            // 留痕失败不回滚业务，后续可用操作日志补偿
        }
    }

    // ---------------------------------------------------------------- 历史沉淀正文

    private String markdown(Task task, List<TaskIssue> issues, List<TaskAction> actions,
                            Map<Long, Dept> depts, Map<Long, String> names) {
        StringBuilder text = new StringBuilder();
        text.append("# 历史问题与措施：").append(task.title).append("\n\n");
        text.append("- 工单：").append(task.taskNo == null ? task.id : task.taskNo).append('\n');
        Dept dept = task.deptId == null ? null : depts.get(task.deptId);
        text.append("- 部门：").append(dept == null ? "—" : dept.getName()).append('\n');
        text.append("- 类型：").append(task.type == null ? "—" : task.type.label()).append('\n');
        text.append("- 完成时间：").append(task.actualEnd == null ? "—" : task.actualEnd.format(MINUTE)).append('\n');
        text.append("- 验收结果：").append(blank(task.resultSummary) ? "—" : task.resultSummary).append('\n');
        text.append('\n').append("## 问题与措施\n");
        if (issues.isEmpty() && actions.isEmpty()) {
            text.append("\n本工单没有单列的问题与措施项。\n");
        }
        int issueSeq = 1;
        for (TaskIssue issue : issues) {
            text.append("\n### 问题 ").append(issueSeq++).append('：').append(issue.content).append('\n');
            text.append("- 分类：").append(blank(issue.category) ? "未分类" : issue.category)
                    .append("；严重度：").append(issue.severity == null ? "—" : issue.severity)
                    .append("；状态：").append(issue.status == null ? "—" : issue.status.label()).append('\n');
            List<TaskAction> paired = actions.stream()
                    .filter(action -> Objects.equals(action.issueId, issue.id))
                    .sorted(Comparator.comparing(action -> action.seq == null ? 0 : action.seq))
                    .toList();
            appendActions(text, paired, names);
        }
        List<TaskAction> orphans = actions.stream()
                .filter(action -> action.issueId == null)
                .toList();
        if (!orphans.isEmpty()) {
            text.append("\n### 其他措施\n");
            appendActions(text, orphans, names);
        }
        text.append("\n## 验收标准\n\n");
        text.append(blank(task.acceptCriteria) ? "—" : task.acceptCriteria).append('\n');
        return text.toString();
    }

    private void appendActions(StringBuilder text, List<TaskAction> actions, Map<Long, String> names) {
        if (actions.isEmpty()) {
            text.append("- 措施：未登记\n");
            return;
        }
        int seq = 1;
        for (TaskAction action : actions) {
            text.append("- 措施 ").append(seq++).append('：').append(action.content)
                    .append("（责任人：").append(action.ownerId == null ? "未指定" : names.getOrDefault(action.ownerId, "—"))
                    .append("；期限：").append(action.dueDate == null ? "未定" : action.dueDate)
                    .append("；状态：").append(action.status == null ? "—" : action.status.label())
                    .append("）\n");
        }
    }

    // ---------------------------------------------------------------- 小工具

    private String meta(KbDocument document) {
        return "{\"domain\":" + quote(document.domain == null ? null : document.domain.name())
                + ",\"deptId\":" + (document.deptId == null ? "null" : document.deptId)
                + ",\"deptCode\":" + quote(document.deptCode)
                + ",\"visibility\":" + quote(document.visibility == null ? null : document.visibility.name())
                + ",\"sourceRef\":" + quote(document.sourceRef) + "}";
    }

    private KbDomain parseDomain(String value) {
        return blank(value) ? null : KbDomain.parse(value.trim());
    }

    private String titleOf(String title, String fileName) {
        String value = trim(title);
        if (value.isEmpty()) {
            int dot = fileName.lastIndexOf('.');
            value = dot > 0 ? fileName.substring(0, dot) : fileName;
        }
        value = trim(value);
        if (value.isEmpty()) {
            value = "未命名文档";
        }
        return limit(value, MAX_TITLE);
    }

    private String excerpt(String content) {
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= EXCERPT_LENGTH ? flat : flat.substring(0, EXCERPT_LENGTH) + "…";
    }

    private boolean supported(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".pdf") || lower.endsWith(".docx") || lower.endsWith(".md")
                || lower.endsWith(".markdown") || lower.endsWith(".txt");
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String messageOf(Throwable error) {
        String message = error.getMessage();
        if (blank(message)) {
            return error.getClass().getSimpleName();
        }
        return limit(message.replaceAll("\\s+", " ").trim(), 300);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
