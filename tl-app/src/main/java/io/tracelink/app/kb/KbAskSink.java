package io.tracelink.app.kb;

import java.util.List;

/** 问答事件流出口：控制器用 SSE 实现，应用层不依赖 Web 类型 */
public interface KbAskSink {

    void retrieved(KbSearchView result);

    void delta(String text);

    void citations(List<KbCitation> citations);

    void done(int answerChars);

    void error(String code, String message);
}
