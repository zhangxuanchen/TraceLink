package io.tracelink.infra.security;

import io.tracelink.domain.auth.PasswordPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordAdapter implements PasswordPort {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null) {
            return false;
        }
        return encoder.matches(rawPassword, passwordHash);
    }
}
