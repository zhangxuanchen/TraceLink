package io.tracelink.domain.ai;

import java.util.function.Consumer;

/** 对话模型端口：应用层只要求「能否用」与「流式产出增量」，实现放在智能层 */
public interface ChatPort {

    boolean available();

    /** 逐段回调回答增量；上游报错、鉴权失败或流中断由实现抛 TL-5031 */
    void stream(String systemPrompt, String userPrompt, Consumer<String> onDelta);
}
