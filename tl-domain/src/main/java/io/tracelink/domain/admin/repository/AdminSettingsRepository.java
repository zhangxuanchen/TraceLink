package io.tracelink.domain.admin.repository;

import io.tracelink.domain.admin.model.AccountInfo;
import io.tracelink.domain.admin.model.RoleInfo;
import io.tracelink.domain.admin.model.SysConfigItem;
import io.tracelink.domain.gate.model.GateRule;

import java.util.List;

/** 系统设置：参数、完成条件权重、角色与账号授权 */
public interface AdminSettingsRepository {

    List<SysConfigItem> listConfigs();

    void updateConfig(String key, String value, Long actorId);

    List<GateRule> listGateRules();

    void updateGateRule(Long id, Integer weight, Boolean enabled, Long actorId);

    List<RoleInfo> listRoles();

    List<AccountInfo> listAccounts();

    void replaceAccountRoles(Long employeeId, List<String> roleCodes);
}
