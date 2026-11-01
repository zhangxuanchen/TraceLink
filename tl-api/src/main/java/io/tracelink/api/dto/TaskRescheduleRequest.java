package io.tracelink.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record TaskRescheduleRequest(
        @NotNull(message = "请填写新期限") LocalDateTime newPlanEnd,
        LocalDateTime oldPlanEnd,
        @NotBlank(message = "请选择改期原因") String reason,
        @Size(max = 1000, message = "不超过 1000 字") String reasonText,
        @Size(max = 1000, message = "不超过 1000 字") String rootCause,
        @Size(max = 1000, message = "不超过 1000 字") String measure) {
}
