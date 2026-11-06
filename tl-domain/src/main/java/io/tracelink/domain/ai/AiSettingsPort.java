package io.tracelink.domain.ai;

/**
 * 模型配置读取端口：智能层每轮生成前取一次，页面保存后下一轮即生效，不需要重启进程。
 */
public interface AiSettingsPort {

    AiSettings current();
}
