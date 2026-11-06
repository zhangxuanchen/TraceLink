package io.tracelink.infra.admin.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.tracelink.domain.admin.model.AccountInfo;
import io.tracelink.domain.admin.model.RoleInfo;
import io.tracelink.domain.admin.model.SysConfigItem;
import io.tracelink.domain.admin.repository.AdminSettingsRepository;
import io.tracelink.domain.gate.model.GateRule;
import io.tracelink.infra.admin.entity.AdminRoleEntity;
import io.tracelink.infra.admin.entity.PermissionEntity;
import io.tracelink.infra.admin.entity.RolePermissionEntity;
import io.tracelink.infra.admin.entity.UserRoleEntity;
import io.tracelink.infra.admin.mapper.AdminRoleMapper;
import io.tracelink.infra.admin.mapper.PermissionMapper;
import io.tracelink.infra.admin.mapper.RolePermissionMapper;
import io.tracelink.infra.admin.mapper.UserRoleMapper;
import io.tracelink.infra.gate.entity.GateRuleEntity;
import io.tracelink.infra.gate.mapper.GateRuleMapper;
import io.tracelink.infra.gate.repository.GateRuleRepositoryImpl;
import io.tracelink.infra.org.entity.DeptEntity;
import io.tracelink.infra.org.entity.EmployeeEntity;
import io.tracelink.infra.org.entity.UserAccountEntity;
import io.tracelink.infra.org.mapper.DeptMapper;
import io.tracelink.infra.org.mapper.EmployeeMapper;
import io.tracelink.infra.org.mapper.UserAccountMapper;
import io.tracelink.infra.task.entity.SysConfigEntity;
import io.tracelink.infra.task.mapper.SysConfigMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class AdminSettingsRepositoryImpl implements AdminSettingsRepository {

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysConfigMapper sysConfigMapper;
    private final GateRuleMapper gateRuleMapper;
    private final AdminRoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserAccountMapper userAccountMapper;
    private final EmployeeMapper employeeMapper;
    private final DeptMapper deptMapper;

    public AdminSettingsRepositoryImpl(SysConfigMapper sysConfigMapper,
                                       GateRuleMapper gateRuleMapper,
                                       AdminRoleMapper roleMapper,
                                       PermissionMapper permissionMapper,
                                       RolePermissionMapper rolePermissionMapper,
                                       UserRoleMapper userRoleMapper,
                                       UserAccountMapper userAccountMapper,
                                       EmployeeMapper employeeMapper,
                                       DeptMapper deptMapper) {
        this.sysConfigMapper = sysConfigMapper;
        this.gateRuleMapper = gateRuleMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.userAccountMapper = userAccountMapper;
        this.employeeMapper = employeeMapper;
        this.deptMapper = deptMapper;
    }

    @Override
    public List<SysConfigItem> listConfigs() {
        return sysConfigMapper.selectList(new QueryWrapper<SysConfigEntity>()
                        .orderByAsc("group_code").orderByAsc("id"))
                .stream()
                .map(e -> new SysConfigItem(e.id, e.cfgKey, e.cfgValue, e.valueType, e.groupCode, e.remark,
                        e.updatedBy, e.updatedAt == null ? null : e.updatedAt.format(MINUTE)))
                .toList();
    }

    @Override
    public void updateConfig(String key, String value, Long actorId) {
        sysConfigMapper.update(null, new UpdateWrapper<SysConfigEntity>()
                .eq("cfg_key", key)
                .set("cfg_value", value)
                .set("updated_by", actorId)
                .set("updated_at", LocalDateTime.now()));
    }

    @Override
    public List<GateRule> listGateRules() {
        return gateRuleMapper.selectList(new QueryWrapper<GateRuleEntity>()
                        .orderByAsc("task_type").orderByAsc("sort_no").orderByAsc("id"))
                .stream().map(GateRuleRepositoryImpl::toModel).toList();
    }

    @Override
    public void updateGateRule(Long id, Integer weight, Boolean enabled, Long actorId) {
        gateRuleMapper.update(null, new UpdateWrapper<GateRuleEntity>()
                .eq("id", id)
                .set(weight != null, "weight", weight)
                .set(enabled != null, "enabled", enabled == null || enabled ? 1 : 0));
    }

    @Override
    public List<RoleInfo> listRoles() {
        List<AdminRoleEntity> roles = roleMapper.selectList(
                new QueryWrapper<AdminRoleEntity>().orderByAsc("id"));
        List<PermissionEntity> permissions = permissionMapper.selectList(
                new QueryWrapper<PermissionEntity>().orderByAsc("id"));
        List<RolePermissionEntity> links = rolePermissionMapper.selectList(null);
        Map<Long, String> permissionNames = new HashMap<>();
        for (PermissionEntity permission : permissions) {
            permissionNames.put(permission.id, permission.code);
        }
        Map<Long, List<String>> byRole = new LinkedHashMap<>();
        for (RolePermissionEntity link : links) {
            String code = permissionNames.get(link.permissionId);
            if (code != null) {
                byRole.computeIfAbsent(link.roleId, k -> new ArrayList<>()).add(code);
            }
        }
        List<RoleInfo> result = new ArrayList<>();
        for (AdminRoleEntity role : roles) {
            result.add(new RoleInfo(role.id, role.code, role.name, role.dataScopeType, role.remark,
                    byRole.getOrDefault(role.id, List.of())));
        }
        return result;
    }

    @Override
    public List<AccountInfo> listAccounts() {
        List<EmployeeEntity> employees = employeeMapper.selectList(
                new QueryWrapper<EmployeeEntity>().orderByAsc("id"));
        List<DeptEntity> depts = deptMapper.selectList(null);
        List<UserAccountEntity> accounts = userAccountMapper.selectList(null);
        List<AdminRoleEntity> roles = roleMapper.selectList(null);
        List<UserRoleEntity> links = userRoleMapper.selectList(null);

        Map<Long, String> deptNames = new HashMap<>();
        for (DeptEntity dept : depts) {
            deptNames.put(dept.getId(), dept.getName());
        }
        Map<Long, String> roleCodes = new HashMap<>();
        Map<Long, String> roleScopes = new HashMap<>();
        for (AdminRoleEntity role : roles) {
            roleCodes.put(role.id, role.code);
            roleScopes.put(role.id, role.dataScopeType);
        }
        Map<Long, List<String>> byEmployee = new LinkedHashMap<>();
        Map<Long, String> widestScope = new HashMap<>();
        for (UserRoleEntity link : links) {
            String code = roleCodes.get(link.roleId);
            if (code != null) {
                byEmployee.computeIfAbsent(link.employeeId, k -> new ArrayList<>()).add(code);
            }
            String scope = roleScopes.get(link.roleId);
            if (scope != null && wider(scope, widestScope.get(link.employeeId))) {
                widestScope.put(link.employeeId, scope);
            }
        }
        List<AccountInfo> result = new ArrayList<>();
        Map<Long, UserAccountEntity> accountByEmployee = new HashMap<>();
        for (UserAccountEntity account : accounts) {
            accountByEmployee.put(account.getEmployeeId(), account);
        }
        for (EmployeeEntity employee : employees) {
            UserAccountEntity account = accountByEmployee.get(employee.getId());
            result.add(new AccountInfo(
                    employee.getId(),
                    employee.getEmpNo(),
                    employee.getName(),
                    deptNames.get(employee.getDeptId()),
                    account == null ? null : account.getUsername(),
                    account == null ? 0 : account.getStatus(),
                    byEmployee.getOrDefault(employee.getId(), List.of()),
                    widestScope.getOrDefault(employee.getId(), "SELF")));
        }
        return result;
    }

    @Override
    public void replaceAccountRoles(Long employeeId, List<String> roleCodes) {
        userRoleMapper.delete(new QueryWrapper<UserRoleEntity>().eq("employee_id", employeeId));
        if (roleCodes == null || roleCodes.isEmpty()) {
            return;
        }
        List<AdminRoleEntity> roles = roleMapper.selectList(new QueryWrapper<AdminRoleEntity>()
                .in("code", roleCodes));
        for (AdminRoleEntity role : roles) {
            UserRoleEntity link = new UserRoleEntity();
            link.employeeId = employeeId;
            link.roleId = role.id;
            userRoleMapper.insert(link);
        }
    }

    private static boolean wider(String candidate, String current) {
        if (candidate == null) {
            return false;
        }
        if (current == null) {
            return true;
        }
        return rank(candidate) < rank(current);
    }

    private static int rank(String scope) {
        return switch (scope) {
            case "ALL" -> 0;
            case "DEPT_TREE" -> 1;
            case "DEPT" -> 2;
            default -> 3;
        };
    }
}
