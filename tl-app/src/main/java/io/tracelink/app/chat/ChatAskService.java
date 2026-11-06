package io.tracelink.app.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracelink.app.kb.KbAppService;
import io.tracelink.app.kb.KbCitation;
import io.tracelink.app.kb.KbSearchView;
import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.ai.ChatPort;
import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.chat.model.ChatMessage;
import io.tracelink.domain.chat.model.ChatRole;
import io.tracelink.domain.chat.model.ChatSession;
import io.tracelink.domain.chat.model.ChatSessionStatus;
import io.tracelink.domain.chat.repository.ChatMessageRepository;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 工单助手问答（CHT-3）：只围绕工单数据回答（进度、期限、阻碍、验收、口径统计与相关制度）。
 * 工具调用与检索都在服务端完成，事件顺序固定：
 * retrieval → tool_call… → delta… → citations → hint → done；模型不可用或失败时降级为规则回答，
 * 并在 done 事件里标 degraded，不阻塞其它请求（DESIGN 4.8、4.9）。
 */
@Service
public class ChatAskService implements DisposableBean {

    private static final int MIN_QUESTION = 1;
    private static final int MAX_QUESTION = 1000;
    private static final int SEARCH_MAX_QUESTION = 500;
    private static final int MAX_HITS = 5;
    private static final int MAX_CITATIONS = 5;
    private static final int CITATION_EXCERPT = 160;
    private static final int RULE_ANSWER_MAX = 900;
    private static final String PROMPT_VER = "m5-chat-5";
    private static final String MODEL_RULE = "RULE";

    private static final String SYSTEM_PROMPT = """
            你是企业执行追踪系统的工单助手。你的职责只有一件事：依据下面给出的工单、统计、
            部门组织三类工具结果与知识片段，回答与工单相关的问题——进度、期限、逾期、阻碍、
            承办与主责、完成条件（缺口）、验收与退回、口径统计、组织分工与人员负载，
            以及这些流程对应的制度依据。不得编造数字、人名或文档内容。
            要求：
            1. 只回答与工单相关的问题；与工单无关的闲聊、通用写作或外部资讯，用一句话说明
               「我只处理工单数据，可以问工单进度、逾期、阻碍、验收或口径统计」，不要展开；
            2. 先给结论，再给依据，用中文，控制在 600 字以内；
            3. 必须做跨源综合：把工单事实、口径统计、知识依据与部门组织（编制、在职、负责人、
               上下级、在办负载）放在一起看，指出主要成因属于「组织与人手」「流程与制度」还是
               「执行时效」，不要只罗列各工具的数字；
            4. 某一维没有数据时，明确说这一维缺失，不要用其它维度推测补位；
            5. 引用知识片段时用方括号编号标注来源，例如 [1]；
            6. 工具结果只说明事实，不做推测；
            7. 提问像是一件新事项时，只提示可以去工单中心建单，不要代填字段，也不要给出建单草稿；
            8. 不输出与工单无关的建议；
            9. 部门、工单类型与时间范围由系统从问题里自动识别并收紧，能识别的不要反问；
               识别不了或问题本身有歧义时（例如「逾期最多」是逾期数量还是逾期率、
               「生产部」是哪个部门、「最近」是多长时间），才需要反问；
            10. 如果需要反问，在回答末尾另起一段，标题为【反问建议】，下面用列表输出 3 条
                具体的澄清方向（每条以 `- ` 开头），每条都是一个具体可填的问句，不要泛泛而问；
                例如不要写「请明确时间范围」，要写「要查最近 30 天、本月还是本周的数据？」；
            11. 如果问题本身已经明确（部门、时间、口径都清楚），不要输出【反问建议】段。
            """;

    private final ChatAppService chatAppService;
    private final ChatToolService toolService;
    private final KbAppService kbAppService;
    private final ChatMessageRepository messageRepository;
    private final ChatPort chatPort;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService askExecutor;
    private final Map<String, Replay> replayCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Replay> eldest) {
                    return size() > 200;
                }
            });

    public ChatAskService(ChatAppService chatAppService,
                          ChatToolService toolService,
                          KbAppService kbAppService,
                          ChatMessageRepository messageRepository,
                          ChatPort chatPort) {
        this.chatAppService = chatAppService;
        this.toolService = toolService;
        this.kbAppService = kbAppService;
        this.messageRepository = messageRepository;
        this.chatPort = chatPort;
        this.askExecutor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "chat-ask");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void destroy() {
        askExecutor.shutdownNow();
    }

    /** 问题长度 1–1000 字，开流前校验，避免事件流已建立才报参数错 */
    public String validate(ChatAskCommand command) {
        String question = command == null || command.question() == null ? "" : command.question().trim();
        if (question.length() < MIN_QUESTION) {
            throw new BizException(ErrorCode.QUESTION_INVALID, "请输入要问的内容");
        }
        if (question.length() > MAX_QUESTION) {
            throw new BizException(ErrorCode.QUESTION_INVALID, "问题不超过 " + MAX_QUESTION + " 字");
        }
        return question;
    }

    /**
     * 开流前做归属与归档校验，并把用户提问先落库；
     * 同键重放不重复写消息，也不重复计费。
     */
    public Prepared prepare(AuthUser actor, Long sessionId, ChatAskCommand command, String idempotencyKey) {
        String question = validate(command);
        return prepare(actor, sessionId, command, idempotencyKey, question);
    }

    public void ask(AuthUser actor, Prepared prepared, ChatAskCommand command, String key, ChatAskSink sink) {
        askExecutor.submit(() -> answer(actor, prepared, command, key, sink));
    }

    private Prepared prepare(AuthUser actor, Long sessionId, ChatAskCommand command,
                            String key, String question) {
        ChatSession session = chatAppService.requireOwned(actor, sessionId);
        if (session.status == ChatSessionStatus.ARCHIVED) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, "会话已归档，不能再提问");
        }
        Replay replay = key == null || key.isBlank() ? null : replayCache.get(key);
        if (replay != null && replay.sessionId().equals(sessionId)) {
            return new Prepared(session, replay);
        }
        chatAppService.appendMessage(session, ChatRole.USER, question);
        int next = session.msgCountValue() + 1;
        chatAppService.touch(session, session.msgCountValue() == 0 ? chatAppService.titleOf(question) : null, next);
        return new Prepared(session, null);
    }

    // ---------------------------------------------------------------- 编排

    private void answer(AuthUser actor, Prepared prepared, ChatAskCommand command, String key, ChatAskSink sink) {
        long start = System.currentTimeMillis();
        ChatSession session = prepared.session();
        try {
            if (prepared.replay() != null) {
                replay(prepared.replay(), sink);
                return;
            }
            String question = validate(command);
            List<ChatToolResult> tools = command.toolsAllowed() ? toolService.run(actor, command) : List.of();
            // 范围不在页面上手选：问题里提到哪个部门就收到哪个部门，没提到时按调用人的可见范围检索
            Long searchDept = command.deptId() != null ? command.deptId() : toolService.deptInQuestion(question);
            KbSearchView retrieval = kbAppService.search(actor, searchQuestion(question), null,
                    searchDept, MAX_HITS);
            List<KbCitation> citations = citations(retrieval);
            ChatRetrieval retrievalEvent = new ChatRetrieval(retrieval.retriever(), retrieval.degraded(),
                    retrieval.total(), chunkIds(retrieval), toolNames(tools, command.toolsAllowed()));
            sink.retrieval(retrievalEvent);
            for (ChatToolResult tool : tools) {
                sink.toolCall(tool.toEvent());
            }
            ChatToolResult knowledge = knowledgeResult(retrieval);
            if (command.toolsAllowed()) {
                sink.toolCall(knowledge.toEvent());
            }

            String answer;
            boolean degraded = false;
            String model = MODEL_RULE;
            if (retrieval.hits().isEmpty() && tools.stream().noneMatch(t -> t.resultCount() > 0)) {
                answer = "没有找到与「" + shorten(question, 60) + "」相关的工单或制度片段。"
                        + "我只处理工单数据，可以问工单进度、逾期、阻碍、验收或口径统计。";
                sink.delta(answer);
            } else if (!chatPort.available()) {
                degraded = true;
                answer = ruleAnswer(question, retrieval, tools, true);
                sink.delta(answer);
            } else {
                StringBuilder buffer = new StringBuilder();
                try {
                    chatPort.stream(SYSTEM_PROMPT, userPrompt(question, retrieval, tools, session),
                            delta -> {
                                buffer.append(delta);
                                sink.delta(delta);
                            });
                    answer = buffer.toString();
                    String currentModel = chatPort.modelName();
                    model = currentModel == null || currentModel.isBlank() ? "ARK" : currentModel;
                } catch (RuntimeException e) {
                    degraded = true;
                    answer = ruleAnswer(question, retrieval, tools, true);
                    sink.delta((buffer.isEmpty() ? "" : "\n\n") + answer);
                }
            }

            // 解析反问建议段：从完整 answer 里剥离出来存到 ChatClarify，
            // chatMessage.content 只保留正文不含【反问建议】段
            List<String> clarifyQuestions = new ArrayList<>();
            String cleanAnswer = answer;
            if (answer != null && answer.contains("【反问建议】")) {
                int idx = answer.indexOf("【反问建议】");
                cleanAnswer = answer.substring(0, idx).trim();
                String block = answer.substring(idx + "【反问建议】".length());
                for (String line : block.split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("- ") && trimmed.length() > 2) {
                        String q = trimmed.substring(2).trim();
                        if (!q.isBlank()) clarifyQuestions.add(q);
                        if (clarifyQuestions.size() >= 3) break;
                    }
                }
            }

            ChatHint hint = hint(question, tools);
            sink.citations(citations);
            if (hint != null) {
                sink.hint(hint);
            }
            if (!clarifyQuestions.isEmpty()) {
                sink.clarify(clarifyQuestions);
            }

            int latency = (int) (System.currentTimeMillis() - start);
            ChatMessage assistant = new ChatMessage();
            assistant.sessionId = session.id;
            assistant.role = ChatRole.ASSISTANT;
            assistant.content = cleanAnswer;
            assistant.citations = json(citations);
            assistant.toolCalls = json(toolEvents(tools, knowledge, command.toolsAllowed()));
            assistant.modelName = model;
            assistant.promptVer = PROMPT_VER;
            assistant.tokenIn = estimateTokens(question);
            assistant.tokenOut = estimateTokens(cleanAnswer);
            assistant.latencyMs = latency;
            messageRepository.insert(assistant);
            chatAppService.touch(session, null, session.msgCountValue() + 1);

            ChatDone done = new ChatDone(cleanAnswer.length(), degraded, model, latency, PROMPT_VER);
            if (key != null && !key.isBlank()) {
                replayCache.put(key, new Replay(session.id, question, retrievalEvent, citations,
                        toolEvents(tools, knowledge, command.toolsAllowed()), cleanAnswer, hint, done));
            }
            sink.done(done);
        } catch (BizException e) {
            sink.error(e.errorCode().code(), e.getMessage());
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            sink.error(ErrorCode.INTERNAL_ERROR.code(), "回答失败：" + message);
        }
    }

    private void replay(Replay replay, ChatAskSink sink) {
        sink.retrieval(replay.retrieval());
        for (Map<String, Object> tool : replay.toolCalls()) {
            sink.toolCall(new ChatToolEvent(String.valueOf(tool.get("name")), String.valueOf(tool.get("label")),
                    argsOf(tool), toInt(tool.get("resultCount")), toLong(tool.get("elapsedMs"))));
        }
        sink.delta(replay.answer());
        sink.citations(replay.citations());
        if (replay.hint() != null) {
            sink.hint(replay.hint());
        }
        sink.done(replay.done());
    }

    // ---------------------------------------------------------------- 规则回答与提示

    private String ruleAnswer(String question, KbSearchView retrieval, List<ChatToolResult> tools,
                              boolean modelUnavailable) {
        StringBuilder text = new StringBuilder();
        text.append(modelUnavailable ? "（模型暂不可用，本次回答由规则拼装）\n\n" : "");
        if (!tools.isEmpty()) {
            text.append("**工具结果**\n");
            for (ChatToolResult tool : tools) {
                text.append("- ").append(tool.label()).append("：共 ").append(tool.resultCount()).append(" 条");
                if (!tool.facts().isEmpty()) {
                    text.append("；").append(String.join("；", tool.facts().subList(0,
                            Math.min(3, tool.facts().size()))));
                }
                text.append('\n');
            }
        }
        List<KbSearchView.Hit> hits = retrieval == null ? List.of() : retrieval.hits();
        if (!hits.isEmpty()) {
            text.append("\n**知识依据**\n");
            int index = 1;
            for (KbSearchView.Hit hit : hits) {
                if (index > MAX_CITATIONS) {
                    break;
                }
                text.append('[').append(index).append("] 文档《").append(hit.docTitle())
                        .append("》第 ").append(hit.seq()).append(" 片：")
                        .append(shorten(hit.excerpt(), 120)).append('\n');
                index++;
            }
        }
        if (retrieval != null && retrieval.degraded()) {
            text.append("\n检索为本地降级结果，相似度仅作排序参考。");
        }
        text.append("\n以上结论只基于系统内的工具结果与知识片段；需要更准确的口径时，可以再问一次或直接查对应页面。");
        return shorten(text.toString(), RULE_ANSWER_MAX);
    }

    private ChatHint hint(String question, List<ChatToolResult> tools) {
        boolean newItem = containsAny(question, "新建", "建单", "申请", "安排", "新增", "没人", "需要做");
        if (!newItem) {
            return null;
        }
        boolean taskHit = tools.stream().anyMatch(t -> "task_query".equals(t.name()) && t.resultCount() > 0);
        if (taskHit) {
            return null;
        }
        return new ChatHint("这看起来是一件新事项。工单助手不代填建单字段，请到工单中心新建，负责人与主责按实际填写。",
                "#/tasks", "CREATE_TASK");
    }

    private ChatToolResult knowledgeResult(KbSearchView retrieval) {
        long elapsed = 0;
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("question", retrieval.question());
        args.put("retriever", retrieval.retriever());
        args.put("topK", MAX_HITS);
        List<String> facts = retrieval.hits().stream()
                .map(h -> "《" + h.docTitle() + "》第 " + h.seq() + " 片：" + shorten(h.excerpt(), 80))
                .toList();
        return new ChatToolResult("knowledge_search", "检索知识", args, retrieval.total(), elapsed, facts);
    }

    // ---------------------------------------------------------------- 组装

    private List<KbCitation> citations(KbSearchView retrieval) {
        List<KbCitation> citations = new ArrayList<>();
        for (KbSearchView.Hit hit : retrieval.hits()) {
            if (citations.size() >= MAX_CITATIONS) {
                break;
            }
            citations.add(new KbCitation(hit.docId(), hit.chunkId(), hit.docTitle(), hit.seq(), hit.pageNo(),
                    shorten(hit.excerpt(), CITATION_EXCERPT)));
        }
        return citations;
    }

    private List<Long> chunkIds(KbSearchView retrieval) {
        return retrieval.hits().stream().map(KbSearchView.Hit::chunkId).filter(java.util.Objects::nonNull)
                .limit(MAX_CITATIONS).toList();
    }

    private List<String> toolNames(List<ChatToolResult> tools, boolean allowTools) {
        List<String> names = new ArrayList<>(tools.stream().map(ChatToolResult::name).toList());
        if (allowTools) {
            names.add("knowledge_search");
        }
        return names;
    }

    private List<Map<String, Object>> toolEvents(List<ChatToolResult> tools, ChatToolResult knowledge,
                                                 boolean allowTools) {
        List<Map<String, Object>> events = new ArrayList<>();
        for (ChatToolResult tool : tools) {
            events.add(toolEvent(tool));
        }
        if (allowTools) {
            events.add(toolEvent(knowledge));
        }
        return events;
    }

    private Map<String, Object> toolEvent(ChatToolResult tool) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", tool.name());
        map.put("label", tool.label());
        map.put("args", tool.args());
        map.put("resultCount", tool.resultCount());
        map.put("elapsedMs", tool.elapsedMs());
        return map;
    }

    private String userPrompt(String question, KbSearchView retrieval, List<ChatToolResult> tools,
                              ChatSession session) {
        StringBuilder prompt = new StringBuilder("问题：").append(question).append("\n\n");
        if (!tools.isEmpty()) {
            prompt.append("【工具结果】\n");
            for (ChatToolResult tool : tools) {
                prompt.append("- ").append(tool.label()).append("（共 ").append(tool.resultCount()).append(" 条）");
                if (!tool.facts().isEmpty()) {
                    prompt.append("：").append(String.join("；", tool.facts()));
                }
                prompt.append('\n');
            }
            prompt.append('\n');
        }
        if (!retrieval.hits().isEmpty()) {
            prompt.append("【知识片段】\n");
            int index = 1;
            for (KbSearchView.Hit hit : retrieval.hits()) {
                if (index > MAX_CITATIONS) {
                    break;
                }
                prompt.append('[').append(index).append("] 文档《").append(hit.docTitle())
                        .append("》第 ").append(hit.seq()).append(" 片");
                if (hit.pageNo() != null) {
                    prompt.append("，第 ").append(hit.pageNo()).append(" 页");
                }
                prompt.append("\n").append(hit.content()).append("\n\n");
                index++;
            }
        }
        List<ChatMessage> history = messageRepository.listBySession(session.id, 20);
        StringBuilder lines = new StringBuilder();
        for (ChatMessage message : history) {
            if (message.role == ChatRole.USER && message.content != null && message.content.equals(question)) {
                continue;
            }
            lines.append(message.role == ChatRole.USER ? "用户：" : "助手：")
                    .append(shorten(message.content, 200)).append('\n');
        }
        if (!lines.isEmpty()) {
            prompt.append("【对话历史】\n").append(lines);
        }
        return prompt.toString();
    }

    // ---------------------------------------------------------------- 工具

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return "[]";
        }
    }

    private int estimateTokens(String text) {
        return text == null ? 0 : Math.max(1, text.length() / 2);
    }

    private String searchQuestion(String question) {
        return question.length() > SEARCH_MAX_QUESTION ? question.substring(0, SEARCH_MAX_QUESTION) : question;
    }

    private String shorten(String value, int max) {
        String text = value == null ? "" : value.trim();
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> argsOf(Map<String, Object> tool) {
        Object args = tool.get("args");
        if (args instanceof Map<?, ?> map) {
            Map<String, Object> typed = new LinkedHashMap<>();
            map.forEach((k, v) -> typed.put(String.valueOf(k), v));
            return typed;
        }
        return Map.of();
    }

    private int toInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    // ---------------------------------------------------------------- 内部类型

    public record Prepared(ChatSession session, Replay replay) {
    }

    public record Replay(Long sessionId, String question, ChatRetrieval retrieval,
                         List<KbCitation> citations, List<Map<String, Object>> toolCalls,
                         String answer, ChatHint hint, ChatDone done) {
    }
}
