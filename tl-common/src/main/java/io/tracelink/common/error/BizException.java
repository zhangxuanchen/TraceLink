package io.tracelink.common.error;

public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static BizException notFound(String message) {
        return new BizException(ErrorCode.NOT_FOUND, message);
    }

    public static BizException duplicate(String message) {
        return new BizException(ErrorCode.DUPLICATE_OBJECT, message);
    }

    public static BizException invalid(String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
