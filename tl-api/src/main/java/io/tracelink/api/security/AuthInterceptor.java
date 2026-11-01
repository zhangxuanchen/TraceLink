package io.tracelink.api.security;

import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.auth.TokenPort;
import io.tracelink.domain.org.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final TokenPort tokenPort;
    private final UserAccountRepository userAccountRepository;

    public AuthInterceptor(TokenPort tokenPort, UserAccountRepository userAccountRepository) {
        this.tokenPort = tokenPort;
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BizException(ErrorCode.UNAUTHENTICATED, "请先登录");
        }
        AuthUser fromToken = tokenPort.parse(header.substring(7).trim());
        AuthUser current = userAccountRepository.loadAuth(fromToken.employeeId());
        if (current == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED, "账号已停用或不存在，请联系管理员");
        }
        AuthContext.set(current);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
    }
}
