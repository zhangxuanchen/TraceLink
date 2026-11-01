package io.tracelink.domain.org.repository;

import java.util.Map;

/** 姓名别名，如 老沈 → 沈桦。导入解析时优先按别名命中 */
public interface EmployeeAliasRepository {

    Map<String, Long> aliases();
}
