package io.tracelink.common.api;

/**
 * 统一返回体。code 为 "0" 表示成功，其余为 docs/DESIGN.md 5.3 的错误码。
 */
public record ApiResult<T>(String code, String message, T data) {

    public static final String SUCCESS_CODE = "0";

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(SUCCESS_CODE, "ok", data);
    }

    public static ApiResult<Void> ok() {
        return new ApiResult<>(SUCCESS_CODE, "ok", null);
    }

    public static <T> ApiResult<T> fail(String code, String message) {
        return new ApiResult<>(code, message, null);
    }
}
