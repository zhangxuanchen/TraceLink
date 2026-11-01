package io.tracelink.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TaskSubmitRequest(
        @NotBlank(message = "请填写结果说明") @Size(max = 2000, message = "不超过 2000 字") String resultSummary,
        List<Boolean> criteriaChecks) {
}
