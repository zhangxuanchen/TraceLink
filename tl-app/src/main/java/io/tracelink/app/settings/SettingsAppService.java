package io.tracelink.app.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.admin.model.AccountInfo;
import io.tracelink.domain.admin.model.RoleInfo;
import io.tracelink.domain.admin.model.SysConfigItem;
import io.tracelink.domain.admin.repository.AdminSettingsRepository;
import io.tracelink.domain.audit.model.AuditLog;
import io.tracelink.domain.audit.repository.AuditLogRepository;
import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.gate.model.GateCode;
import io.tracelink.domain.gate.model.GateRule;
import io.tracelink.domain.task.model.TaskType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 模板与参数页、角色与数据范围页的应用服务。
 * 权重改动直接写 gate_rule，完成地图、矩阵与提醒下一轮即读到新值（DESIGN 4.10「改库即时生效」）。
 */
@Service
public class SettingsAppService {

    private static final String ROLE_ADMIN = "SYS_ADMIN";
    private static final String ROLE_MANAGER = "MANAGER";
    private static final String TYPE_ALL = "ALL";
    private static final Map<String, String> SCOPE_LABELS = Map.of(
            "ALL", "全部数据",
            "DEPT_TREE", "本部门及下级",
            "DEPT", "本部门",
            "SELF", "仅本人");

    private final AdminSettingsRepository settingsRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public SettingsAppService(AdminSettingsRepository settingsRepository,
                              AuditLogRepository auditLogRepository,
                              ObjectMapper objectMapper) {
        this.settingsRepository = settingsRepository;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- 参数

    public List<ConfigView> configs(AuthUser actor) {
        requireRead(actor);
        return settingsRepository.listConfigs().stream().map(this::toConfigView).toList();
    }

    @Transactional
    public ConfigView updateConfig(AuthUser actor, String key, String value) {
        requireAdmin(actor);
        if (key == null || key.isBlank()) {
            throw BizException.invalid("请指定要修改的参数键");
        }
        SysConfigItem item = settingsRepository.listConfigs().stream()
                .filter(c -> c.cfgKey().equals(key))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "参数不存在：" + key));
        String normalized = validateValue(item, value);
        if (normalized.equals(item.cfgValue())) {
            return toConfigView(item);
        }
        settingsRepository.updateConfig(key, normalized, actor.employeeId());
        audit(actor, "UPDATE", "SYS_CONFIG", item.id(), Map.of("key", key, "value", item.cfgValue()),
                Map.of("key", key, "value", normalized));
        return settingsRepository.listConfigs().stream()
                .filter(c -> c.cfgKey().equals(key))
                .findFirst().map(this::toConfigView)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "参数已删除：" + key));
    }

    // ---------------------------------------------------------------- 完成条件权重

    public GateSettingsView gateRules(AuthUser actor) {
        requireRead(actor);
        List<GateRule> rules = settingsRepository.listGateRules();
        List<GateRuleGroupView> groups = new ArrayList<>();
        groups.add(groupOf(TYPE_ALL, "全部类型默认口径", rules));
        for (TaskType type : TaskType.values()) {
            groups.add(groupOf(type.name(), type.label(), rules));
        }
        return new GateSettingsView("v1", groups);
    }

    @Transactional
    public GateSettingsView updateGateRule(AuthUser actor, Long id, Integer weight, Boolean enabled) {
        requireAdmin(actor);
        List<GateRule> rules = new ArrayList<>(settingsRepository.listGateRules());
        GateRule target = rules.stream().filter(r -> r.id != null && r.id.equals(id)).findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "完成条件配置不存在：" + id));
        if (weight != null && (weight < 0 || weight > 100)) {
            throw BizException.invalid("权重取值 0–100");
        }
        int newWeight = weight == null ? target.weightValue() : weight;
        boolean newEnabled = enabled == null ? target.enabledValue() : enabled;
        GateRule simulated = copy(target);
        simulated.weight = newWeight;
        simulated.enabled = newEnabled;
        List<GateRule> next = rules.stream()
                .map(r -> r.id != null && r.id.equals(id) ? simulated : r)
                .toList();
        List<String> affected = TYPE_ALL.equalsIgnoreCase(target.taskType)
                ? allTypeCodes() : List.of(target.taskType);
        List<String> problems = new ArrayList<>();
        for (String type : affected) {
            int total = effectiveRules(type, next).stream()
                    .filter(GateRule::enabledValue)
                    .mapToInt(GateRule::weightValue)
                    .sum();
            if (total != 100) {
                problems.add(typeLabel(type) + "合计 " + total);
            }
        }
        if (!problems.isEmpty()) {
            throw new BizException(ErrorCode.RULE_UNSATISFIED,
                    "权重合计须为一百，" + String.join("、", problems)
                            + "；请同步调整其他条件，或把停用项权重改为 0");
        }
        settingsRepository.updateGateRule(id, weight, enabled, actor.employeeId());
        audit(actor, "UPDATE", "GATE_RULE", id,
                Map.of("weight", target.weightValue(), "enabled", target.enabledValue()),
                Map.of("weight", newWeight, "enabled", newEnabled));
        return gateRules(actor);
    }

    // ---------------------------------------------------------------- 角色与账号

    public List<RoleView> roles(AuthUser actor) {
        requireRead(actor);
        return settingsRepository.listRoles().stream().map(SettingsAppService::toRoleView).toList();
    }

    public List<AccountView> accounts(AuthUser actor) {
        requireRead(actor);
        return settingsRepository.listAccounts().stream().map(SettingsAppService::toAccountView).toList();
    }

    @Transactional
    public List<AccountView> updateAccountRoles(AuthUser actor, Long employeeId, List<String> roleCodes) {
        requireAdmin(actor);
        if (employeeId == null) {
            throw BizException.invalid("请指定人员");
        }
        if (employeeId.equals(actor.employeeId())) {
            throw new BizException(ErrorCode.RULE_UNSATISFIED, "不能修改自己的角色，请让另一位系统管理员操作");
        }
        Set<String> known = settingsRepository.listRoles().stream()
                .map(RoleInfo::code).collect(Collectors.toSet());
        List<String> requested = roleCodes == null ? List.of() : roleCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .toList();
        for (String code : requested) {
            if (!known.contains(code)) {
                throw BizException.invalid("未知角色：" + code);
            }
        }
        AccountInfo before = settingsRepository.listAccounts().stream()
                .filter(a -> a.employeeId().equals(employeeId)).findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "人员不存在：" + employeeId));
        settingsRepository.replaceAccountRoles(employeeId, requested);
        audit(actor, "UPDATE", "ACCOUNT", employeeId,
                Map.of("roles", before.roleCodes()),
                Map.of("roles", requested));
        return accounts(actor);
    }

    // ---------------------------------------------------------------- 内部

    private GateRuleGroupView groupOf(String typeCode, String label, List<GateRule> all) {
        List<GateRule> effective = effectiveRules(typeCode, all);
        int total = effective.stream().filter(GateRule::enabledValue)
                .mapToInt(GateRule::weightValue).sum();
        List<GateRuleRowView> rows = effective.stream().map(rule -> new GateRuleRowView(
                rule.id,
                rule.taskType,
                rule.gateCode == null ? null : rule.gateCode.name(),
                rule.gateCode == null ? "" : rule.gateCode.title(),
                rule.weightValue(),
                rule.requiredValue(),
                rule.holderRole == null ? null : rule.holderRole.name(),
                rule.holderRole == null ? null : rule.holderRole.label(),
                rule.enabledValue(),
                !TYPE_ALL.equalsIgnoreCase(typeCode) && TYPE_ALL.equalsIgnoreCase(rule.taskType)
        )).toList();
        return new GateRuleGroupView(typeCode, label, total, total == 100, rows);
    }

    /** 先铺 ALL，再按类型覆盖，与 GateEvaluator 的口径完全一致 */
    private List<GateRule> effectiveRules(String typeCode, List<GateRule> all) {
        Map<GateCode, GateRule> merged = new EnumMap<>(GateCode.class);
        for (GateRule rule : all) {
            if (rule.gateCode != null && TYPE_ALL.equalsIgnoreCase(rule.taskType)) {
                merged.put(rule.gateCode, rule);
            }
        }
        if (!TYPE_ALL.equalsIgnoreCase(typeCode)) {
            for (GateRule rule : all) {
                if (rule.gateCode != null && typeCode.equalsIgnoreCase(rule.taskType)) {
                    merged.put(rule.gateCode, rule);
                }
            }
        }
        List<GateRule> rules = new ArrayList<>(merged.values());
        rules.sort(Comparator.comparingInt(r -> r.sortNo == null ? 99 : r.sortNo));
        return rules;
    }

    private List<String> allTypeCodes() {
        List<String> codes = new ArrayList<>();
        codes.add(TYPE_ALL);
        for (TaskType type : TaskType.values()) {
            codes.add(type.name());
        }
        return codes;
    }

    private String typeLabel(String code) {
        if (TYPE_ALL.equalsIgnoreCase(code)) {
            return "默认口径";
        }
        try {
            return TaskType.valueOf(code).label() + "类";
        } catch (IllegalArgumentException e) {
            return code;
        }
    }

    private static GateRule copy(GateRule source) {
        GateRule copy = new GateRule();
        copy.id = source.id;
        copy.taskType = source.taskType;
        copy.gateCode = source.gateCode;
        copy.weight = source.weight;
        copy.required = source.required;
        copy.holderRole = source.holderRole;
        copy.sortNo = source.sortNo;
        copy.enabled = source.enabled;
        return copy;
    }

    private String validateValue(SysConfigItem item, String raw) {
        if (raw == null || raw.isBlank()) {
            throw BizException.invalid("参数值不能为空");
        }
        String value = raw.trim();
        String type = item.valueType() == null ? "STRING" : item.valueType();
        try {
            switch (type) {
                case "INT" -> Integer.parseInt(value);
                case "DECIMAL" -> new BigDecimal(value);
                case "BOOLEAN" -> {
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                        throw BizException.invalid("布尔参数只能填 true 或 false");
                    }
                    value = value.toLowerCase();
                }
                case "JSON" -> objectMapper.readTree(value);
                default -> {
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.invalid("参数类型为 " + type + "，当前值不合法：" + value);
        }
        return value;
    }

    private ConfigView toConfigView(SysConfigItem item) {
        return new ConfigView(item.id(), item.cfgKey(), item.cfgValue(), item.valueType(),
                item.groupCode(), item.remark(), item.updatedBy(), item.updatedAt());
    }

    private static RoleView toRoleView(RoleInfo role) {
        return new RoleView(role.id(), role.code(), role.name(), role.dataScopeType(),
                SCOPE_LABELS.getOrDefault(role.dataScopeType(), role.dataScopeType()),
                role.remark(), role.permissions());
    }

    private static AccountView toAccountView(AccountInfo account) {
        return new AccountView(account.employeeId(), account.empNo(), account.name(), account.deptName(),
                account.username(), account.status(), statusLabel(account.status()),
                account.roleCodes(), account.dataScopeType(),
                SCOPE_LABELS.getOrDefault(account.dataScopeType(), account.dataScopeType()));
    }

    private static String statusLabel(int status) {
        return switch (status) {
            case 1 -> "启用";
            case 2 -> "停用";
            default -> "未开通";
        };
    }

    private void requireRead(AuthUser actor) {
        if (!hasRole(actor, ROLE_ADMIN) && !hasRole(actor, ROLE_MANAGER)) {
            throw new BizException(ErrorCode.NO_PERMISSION, "模板与参数仅管理者可见");
        }
    }

    private void requireAdmin(AuthUser actor) {
        if (!hasRole(actor, ROLE_ADMIN)) {
            throw new BizException(ErrorCode.NO_PERMISSION, "只有系统管理员可以修改配置");
        }
    }

    private static boolean hasRole(AuthUser actor, String role) {
        return actor != null && actor.roles() != null && actor.roles().contains(role);
    }

    private void audit(AuthUser actor, String action, String bizType, Long bizId,
                       Map<String, Object> before, Map<String, Object> after) {
        try {
            AuditLog log = new AuditLog();
            log.actorId = actor.employeeId();
            log.action = action;
            log.bizType = bizType;
            log.bizId = bizId == null ? 0L : bizId;
            log.before = objectMapper.writeValueAsString(new LinkedHashMap<>(before));
            log.after = objectMapper.writeValueAsString(new LinkedHashMap<>(after));
            auditLogRepository.insert(log);
        } catch (Exception e) {
            // 审计失败不阻断配置修改，页面提示与业务数据仍以主事务为准
        }
    }
}
