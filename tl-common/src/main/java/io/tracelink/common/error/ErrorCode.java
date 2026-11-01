package io.tracelink.common.error;

/**
 * 错误码与 docs/DESIGN.md 5.3 对齐。code 直接进响应体，httpStatus 决定状态码。
 */
public enum ErrorCode {

    PARAM_INVALID("TL-4001", 400, "参数校验失败"),
    UNAUTHENTICATED("TL-4011", 401, "未登录或令牌失效"),
    NO_PERMISSION("TL-4031", 403, "无该操作权限"),
    OUT_OF_SCOPE("TL-4032", 403, "超出数据范围"),
    NOT_FOUND("TL-4041", 404, "对象不存在或已删除"),
    STATE_NOT_ALLOWED("TL-4091", 409, "当前状态不允许该操作"),
    DUPLICATE_REQUEST("TL-4092", 409, "幂等键冲突或重复提交"),
    DUPLICATE_OBJECT("TL-4093", 409, "唯一性冲突"),
    RULE_UNSATISFIED("TL-4221", 422, "业务规则不满足"),
    PROGRESS_REGRESSION("TL-4222", 422, "进度回退"),
    RESULT_WITHOUT_EVIDENCE("TL-4223", 422, "结果类记录缺少证据"),
    REJECT_WITHOUT_REASON("TL-4224", 422, "退回未填理由"),
    CRITERIA_UNJUDGED("TL-4225", 422, "验收条件存在未判定项"),
    RATE_LIMITED("TL-4291", 429, "触发限流"),
    INTERNAL_ERROR("TL-5001", 500, "内部错误"),
    DEPENDENCY_UNAVAILABLE("TL-5031", 503, "外部依赖不可用");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(String code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
