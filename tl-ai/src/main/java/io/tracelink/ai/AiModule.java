package io.tracelink.ai;

/**
 * 智能层占位。M4 起放入 ExcelParserAgent、TaskExtractAgent、EvaluatorAgent 等实现，
 * 依赖方向保持 api -> app -> domain <- infra，智能层不直接写业务表。
 */
public final class AiModule {

    private AiModule() {
    }
}
