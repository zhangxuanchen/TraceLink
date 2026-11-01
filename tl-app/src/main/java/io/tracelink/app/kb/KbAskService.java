package io.tracelink.app.kb;

import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.ai.ChatPort;
import io.tracelink.domain.auth.AuthUser;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 带引用的知识问答（KB-3）。检索与范围复核复用 KbAppService，
 * 生成只把通过复核的片段交给模型，事件流顺序固定为 retrieved → delta… → citations → done；
 * 模型不可用时在事件流内发 error（TL-5031）后关闭，不落任何半截回答。
 */
@Service
public class KbAskService implements DisposableBean {

    private static final int MIN_QUESTION = 3;
    private static final int MAX_QUESTION = 500;
    private static final int MAX_CITATIONS = 5;
    private static final int CITATION_EXCERPT = 160;

    private static final String SYSTEM_PROMPT = """
            你是企业执行追踪系统的知识助手。只能依据用户消息里给出的资料片段回答，不得编造资料里没有的内容。
            回答要求：
            1. 先给结论，再给依据，用中文，控制在 600 字以内；
            2. 每条依据后用方括号编号标注来源，例如 [1]；
            3. 资料不足时直接说明不足，并提示可以补充知识或建单追踪；
            4. 不输出与问题无关的建议，不透露资料之外的人名与文档标题。
            """;

    private final KbAppService kbAppService;
    private final ChatPort chatPort;
    private final ExecutorService askExecutor;

    public KbAskService(KbAppService kbAppService, ChatPort chatPort) {
        this.kbAppService = kbAppService;
        this.chatPort = chatPort;
        this.askExecutor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "kb-ask");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void destroy() {
        askExecutor.shutdownNow();
    }

    /** 同步校验问题长度（3 至 500 字），控制器据此在建立事件流前返回 TL-4228 */
    public String validate(KbAskCommand command) {
        String question = command == null || command.question() == null ? "" : command.question().trim();
        if (question.length() < MIN_QUESTION) {
            throw new BizException(ErrorCode.QUESTION_INVALID, "问题至少 " + MIN_QUESTION + " 个字");
        }
        if (question.length() > MAX_QUESTION) {
            throw new BizException(ErrorCode.QUESTION_INVALID, "问题不超过 " + MAX_QUESTION + " 字");
        }
        return question;
    }

    public void ask(AuthUser actor, KbAskCommand command, KbAskSink sink) {
        String question = validate(command);
        askExecutor.submit(() -> answer(actor, command, question, sink));
    }

    private void answer(AuthUser actor, KbAskCommand command, String question, KbAskSink sink) {
        try {
            int topK = command.topK() <= 0 ? 6 : Math.max(5, Math.min(8, command.topK()));
            KbSearchView retrieved = kbAppService.search(actor, question,
                    command.domain() == null ? null : command.domain().name(), command.deptId(), topK);
            sink.retrieved(retrieved);
            List<KbSearchView.Hit> hits = retrieved.hits();
            if (hits.isEmpty()) {
                String guidance = "知识库里没有找到与「" + question + "」相关的资料。"
                        + "可以换个说法再问，或先在知识库上传相关制度、把历史工单的问题与措施沉淀进来。";
                sink.delta(guidance);
                sink.citations(List.of());
                sink.done(guidance.length());
                return;
            }
            if (!chatPort.available()) {
                sink.error(ErrorCode.DEPENDENCY_UNAVAILABLE.code(),
                        "未配置 Ark 密钥或对话模型，暂时不能生成回答，可先用检索结果里的片段");
                return;
            }
            List<KbCitation> citations = citations(hits);
            StringBuilder answer = new StringBuilder();
            chatPort.stream(SYSTEM_PROMPT, userPrompt(question, hits), delta -> {
                answer.append(delta);
                sink.delta(delta);
            });
            sink.citations(citations);
            sink.done(answer.length());
        } catch (BizException e) {
            sink.error(e.errorCode().code(), e.getMessage());
        } catch (RuntimeException e) {
            sink.error(ErrorCode.DEPENDENCY_UNAVAILABLE.code(),
                    "回答生成失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private List<KbCitation> citations(List<KbSearchView.Hit> hits) {
        List<KbCitation> citations = new ArrayList<>();
        for (KbSearchView.Hit hit : hits) {
            if (citations.size() >= MAX_CITATIONS) {
                break;
            }
            String excerpt = hit.excerpt() == null ? "" : hit.excerpt();
            if (excerpt.length() > CITATION_EXCERPT) {
                excerpt = excerpt.substring(0, CITATION_EXCERPT) + "…";
            }
            citations.add(new KbCitation(hit.docId(), hit.chunkId(), hit.docTitle(), hit.seq(),
                    hit.pageNo(), excerpt));
        }
        return citations;
    }

    private String userPrompt(String question, List<KbSearchView.Hit> hits) {
        StringBuilder prompt = new StringBuilder("问题：").append(question).append("\n\n资料片段：\n");
        int index = 1;
        for (KbSearchView.Hit hit : hits) {
            if (index > MAX_CITATIONS) {
                break;
            }
            prompt.append('[').append(index).append("] 文档《").append(hit.docTitle())
                    .append("》第 ").append(hit.seq()).append(" 片");
            if (hit.pageNo() != null) {
                prompt.append("，第 ").append(hit.pageNo()).append(" 页");
            }
            if (hit.domainLabel() != null) {
                prompt.append("，知识域：").append(hit.domainLabel());
            }
            prompt.append("\n").append(hit.content()).append("\n\n");
            index++;
        }
        return prompt.toString();
    }
}
