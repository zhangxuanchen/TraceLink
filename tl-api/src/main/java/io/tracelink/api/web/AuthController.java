package io.tracelink.api.web;

import io.tracelink.api.dto.LoginRequest;
import io.tracelink.api.security.AuthContext;
import io.tracelink.app.auth.AuthAppService;
import io.tracelink.app.auth.LoginResult;
import io.tracelink.common.api.ApiResult;
import io.tracelink.domain.auth.AuthUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthAppService authAppService;

    public AuthController(AuthAppService authAppService) {
        this.authAppService = authAppService;
    }

    @PostMapping("/login")
    public ApiResult<LoginResult> login(@Valid @RequestBody LoginRequest request) {
        return ApiResult.ok(authAppService.login(request.username(), request.password()));
    }

    @GetMapping("/me")
    public ApiResult<AuthUser> me() {
        return ApiResult.ok(AuthContext.current());
    }
}
