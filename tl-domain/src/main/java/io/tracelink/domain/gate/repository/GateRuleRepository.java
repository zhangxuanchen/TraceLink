package io.tracelink.domain.gate.repository;

import io.tracelink.domain.gate.model.GateRule;

import java.util.List;

/** 完成条件口径读取。每次判定都现取，改配置即时生效，不做进程内缓存。 */
public interface GateRuleRepository {

    /** 全部口径，含 ALL 默认行与按类型覆盖行 */
    List<GateRule> findAll();
}
