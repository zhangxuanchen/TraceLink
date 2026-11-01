package io.tracelink.app.auth;

public record LoginResult(String token, String username, String name, Long employeeId,
                          java.util.List<String> roles, String dataScopeType) {
}
