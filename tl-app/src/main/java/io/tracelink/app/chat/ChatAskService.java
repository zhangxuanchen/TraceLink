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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 会话问答（CHT-3）。工具调用与检索都在服务端完成，事件顺序固定：
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
    private static final String PROMPT_VER = "m5-chat-1";
    private static final String MODEL_RULE = "RULE";

    private static final String SYSTEM_PROMPT = """
            你是企业执行追踪系统的会话助手。只能依据下面给出的工具结果与知识片段回答，不得编造数字、人名或文档内容。
            要求：
            1. 先给结论，再给依据，用中文，控制在 600 字以内；
            2. 引用知识片段时用方括号编号标注来源，例如 [1]；
            3. 工具结果只说明事实，不做推测；数据不足时直接说明不足；
            4. 提问像是一件新事项时，只提示可以去工单中心建单，不要代填字段，也不要给出建单草稿；
            5. 不输出与问题无关的建议。
            """;

    private final ChatAppService chatAppService;
    private final ChatToolService toolService;
    private final KbAppService kbAppService;
    private final ChatMessageRepository messageRepository;
    private final ChatPort chatPort;
    private final String modelName;
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
                          ChatPort chatPort,
                          @Value("${tracelink.ai.ark.chat.model:}") String modelName) {
        this.chatAppService = chatAppService;
        this.toolService = toolService;
        this.kbAppService = kbAppService;
        this.messageRepository = messageRepository;
        this.chatPort = chatPort;
        this.modelName = modelName == null ? "" : modelName.trim();
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
            KbSearchView retrieval = kbAppService.search(actor, searchQuestion(question), null,
                    command.deptId(), MAX_HITS);
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
                answer = "没有找到与「" + shorten(question, 60) + "」相关的知识与工单。"
                        + "可以换个说法再问，或先补充知识、就这件事建单追踪。";
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
                    model = modelName.isEmpty() ? "ARK" : modelName;
                } catch (RuntimeException e) {
                    degraded = true;
                    answer = ruleAnswer(question, retrieval, tools, true);
                    sink.delta((buffer.isEmpty() ? "" : "\n\n") + answer);
                }
            }

            ChatHint hint = hint(question, tools);
            sink.citations(citations);
            if (hint != null) {
                sink.hint(hint);
            }

            int latency = (int) (System.currentTimeMillis() - start);
            ChatMessage assistant = new ChatMessage();
            assistant.sessionId = session.id;
            assistant.role = ChatRole.ASSISTANT;
            assistant.content = answer;
            assistant.citations = json(citations);
            assistant.toolCalls = json(toolEvents(tools, knowledge, command.toolsAllowed()));
            assistant.modelName = model;
            assistant.promptVer = PROMPT_VER;
            assistant.tokenIn = estimateTokens(question);
            assistant.tokenOut = estimateTokens(answer);
            assistant.latencyMs = latency;
            messageRepository.insert(assistant);
            chatAppService.touch(session, null, session.msgCountValue() + 1);

            ChatDone done = new ChatDone(answer.length(), degraded, model, latency, PROMPT_VER);
            if (key != null && !key.isBlank()) {
                replayCache.put(key, new Replay(session.id, question, retrievalEvent, citations,
                        toolEvents(tools, knowledge, command.toolsAllowed()), answer, hint, done));
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
        return new ChatHint("这看起来是一件新事项。会话不代填建单字段，请到工单中心新建，负责人与主责按实际填写。",
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
