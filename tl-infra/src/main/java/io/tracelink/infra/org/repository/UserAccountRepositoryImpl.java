package io.tracelink.infra.org.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.org.model.UserAccount;
import io.tracelink.domain.org.repository.UserAccountRepository;
import io.tracelink.infra.org.entity.EmployeeEntity;
import io.tracelink.infra.org.entity.UserAccountEntity;
import io.tracelink.infra.org.mapper.EmployeeMapper;
import io.tracelink.infra.org.mapper.RoleMapper;
import io.tracelink.infra.org.mapper.UserAccountMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class UserAccountRepositoryImpl implements UserAccountRepository {

    private final UserAccountMapper userAccountMapper;
    private final EmployeeMapper employeeMapper;
    private final RoleMapper roleMapper;

    public UserAccountRepositoryImpl(UserAccountMapper userAccountMapper,
                                     EmployeeMapper employeeMapper,
                                     RoleMapper roleMapper) {
        this.userAccountMapper = userAccountMapper;
        this.employeeMapper = employeeMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public UserAccount findByUsername(String username) {
        UserAccountEntity entity = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccountEntity>()
                .eq(UserAccountEntity::getUsername, username));
        if (entity == null) {
            return null;
        }
        UserAccount account = new UserAccount();
        account.setId(entity.getId());
        account.setEmployeeId(entity.getEmployeeId());
        account.setUsername(entity.getUsername());
        account.setPasswordHash(entity.getPasswordHash());
        account.setStatus(entity.getStatus());

        EmployeeEntity employee = employeeMapper.selectById(entity.getEmployeeId());
        if (employee != null) {
            account.setEmployeeName(employee.getName());
            account.setDeptId(employee.getDeptId());
        }
        List<String> roles = roleMapper.selectRoleCodes(entity.getEmployeeId());
        account.setRoleCodes(roles == null ? List.of() : roles);
        account.setDataScopeType(roleMapper.selectWidestDataScope(entity.getEmployeeId()));
        return account;
    }

    @Override
    public AuthUser loadAuth(Long employeeId) {
        EmployeeEntity employee = employeeMapper.selectById(employeeId);
        if (employee == null) {
            return null;
        }
        UserAccountEntity account = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccountEntity>()
                .eq(UserAccountEntity::getEmployeeId, employeeId));
        if (account == null || account.getStatus() == null || account.getStatus() != 1) {
            return null;
        }
        List<String> roles = roleMapper.selectRoleCodes(employeeId);
        return new AuthUser(
                employeeId,
                account.getUsername(),
                employee.getName(),
                employee.getDeptId(),
                roles == null ? List.of() : roles,
                roleMapper.selectWidestDataScope(employeeId));
    }

    @Override
    public void touchLastLogin(Long accountId) {
        userAccountMapper.update(null, new LambdaUpdateWrapper<UserAccountEntity>()
                .eq(UserAccountEntity::getId, accountId)
                .set(UserAccountEntity::getLastLoginAt, LocalDateTime.now()));
    }
}
