package io.tracelink.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TaskVerifyRequest(
        @NotBlank(message = "请选择验收结论") String decision,
        @Size(max = 2000, message = "不超过 2000 字") String resultSummary,
        @Size(max = 1000, message = "不超过 1000 字") String rejectReason,
        List<Boolean> criteriaChecks) {
}
