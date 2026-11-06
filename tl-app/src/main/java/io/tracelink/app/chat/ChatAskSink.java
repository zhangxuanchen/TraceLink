package io.tracelink.app.chat;

import java.util.List;

/** 会话事件流出口：控制器用 SSE 实现，应用层不依赖 Web 类型（DESIGN 5.2） */
public interface ChatAskSink {

    void retrieval(ChatRetrieval retrieval);

    void toolCall(ChatToolEvent event);

    void delta(String text);

    void citations(List<io.tracelink.app.kb.KbCitation> citations);

    void hint(ChatHint hint);

    /** 信息不明确时的澄清建议：最多 3 条，每条一个具体澄清方向 */
    void clarify(List<String> questions);

    void done(ChatDone done);

    void error(String code, String message);
}
