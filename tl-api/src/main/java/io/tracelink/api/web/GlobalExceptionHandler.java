package io.tracelink.api.web;

import io.tracelink.common.api.ApiResult;
import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResult<Void>> handleBiz(BizException e) {
        ErrorCode code = e.errorCode();
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResult.fail(code.code(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleInvalid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(this::describe)
                .collect(Collectors.joining("；"));
        return ResponseEntity.badRequest()
                .body(ApiResult.fail(ErrorCode.PARAM_INVALID.code(), detail));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResult<Void>> handleParam(Exception e) {
        return ResponseEntity.badRequest()
                .body(ApiResult.fail(ErrorCode.PARAM_INVALID.code(), "请求参数不完整或格式不正确"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleUnexpected(Exception e) {
        log.error("unexpected error", e);
        return ResponseEntity.internalServerError()
                .body(ApiResult.fail(ErrorCode.INTERNAL_ERROR.code(), "服务内部错误，请稍后重试"));
    }

    private String describe(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }
}
