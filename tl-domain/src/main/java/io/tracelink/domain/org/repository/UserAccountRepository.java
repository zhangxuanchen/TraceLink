package io.tracelink.domain.org.repository;

import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.org.model.UserAccount;

public interface UserAccountRepository {

    UserAccount findByUsername(String username);

    /**
     * 按人员装载鉴权信息。角色与数据范围每次请求现取，令牌里不带，避免改范围后要等令牌过期。
     */
    AuthUser loadAuth(Long employeeId);

    void touchLastLogin(Long accountId);
}
