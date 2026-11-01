package io.tracelink.domain.auth;

public interface TokenPort {

    String issue(AuthUser user);

    AuthUser parse(String token);
}
