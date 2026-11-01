package io.tracelink.domain.auth;

public interface PasswordPort {

    boolean matches(String rawPassword, String passwordHash);
}
