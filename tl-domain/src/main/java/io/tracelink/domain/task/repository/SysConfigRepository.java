package io.tracelink.domain.task.repository;

/** 参数配置读取，阈值与提醒节奏从 sys_config 取，不在代码里写死 */
public interface SysConfigRepository {

    String get(String key, String defaultValue);

    int getInt(String key, int defaultValue);

    double getDouble(String key, double defaultValue);
}
