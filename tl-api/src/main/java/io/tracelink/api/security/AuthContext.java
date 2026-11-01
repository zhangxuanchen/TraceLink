package io.tracelink.api.security;

import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.auth.AuthUser;

public final class AuthContext {

    private static final ThreadLocal<AuthUser> CURRENT = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthUser user) {
        CURRENT.set(user);
    }

    public static AuthUser current() {
        AuthUser user = CURRENT.get();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return user;
    }

    public static String username() {
        return current().username();
    }

    public static boolean hasRole(String roleCode) {
        return current().roles().contains(roleCode);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
