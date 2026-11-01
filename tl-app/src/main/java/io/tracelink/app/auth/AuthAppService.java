package io.tracelink.app.auth;

import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.auth.PasswordPort;
import io.tracelink.domain.auth.TokenPort;
import io.tracelink.domain.org.model.UserAccount;
import io.tracelink.domain.org.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthAppService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordPort passwordPort;
    private final TokenPort tokenPort;

    public AuthAppService(UserAccountRepository userAccountRepository,
                          PasswordPort passwordPort,
                          TokenPort tokenPort) {
        this.userAccountRepository = userAccountRepository;
        this.passwordPort = passwordPort;
        this.tokenPort = tokenPort;
    }

    public LoginResult login(String username, String rawPassword) {
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw BizException.invalid("请填写账号与口令");
        }
        UserAccount account = userAccountRepository.findByUsername(username.trim());
        if (account == null || !passwordPort.matches(rawPassword, account.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHENTICATED, "账号或口令不正确");
        }
        if (account.getStatus() == null || account.getStatus() != 1) {
            throw new BizException(ErrorCode.NO_PERMISSION, "该账号已停用，请联系管理员");
        }
        AuthUser user = new AuthUser(
                account.getEmployeeId(),
                account.getUsername(),
                account.getEmployeeName(),
                account.getDeptId(),
                account.getRoleCodes(),
                account.getDataScopeType());
        String token = tokenPort.issue(user);
        userAccountRepository.touchLastLogin(account.getId());
        return new LoginResult(token, user.username(), user.name(), user.employeeId(),
                user.roles(), user.dataScopeType());
    }
}
