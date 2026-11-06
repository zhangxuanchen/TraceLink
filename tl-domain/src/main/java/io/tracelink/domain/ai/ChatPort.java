package io.tracelink.domain.ai;

import java.util.function.Consumer;

/** 对话模型端口：应用层只要求「能否用」与「流式产出增量」，实现放在智能层 */
public interface ChatPort {

    boolean available();

    /** 当前生效的模型标识，页面「模型配置」保存后立即变；不实现时由调用方回落到环境变量 */
    default String modelName() {
        return "";
    }

    /** 逐段回调回答增量；上游报错、鉴权失败或流中断由实现抛 TL-5031 */
    void stream(String systemPrompt, String userPrompt, Consumer<String> onDelta);
}
