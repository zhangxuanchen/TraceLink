package io.tracelink.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TaskCheckinRequest(
        @NotNull(message = "请填写进度") @Min(value = 0, message = "进度取 0–100")
        @Max(value = 100, message = "进度取 0–100") Integer progress,
        @NotBlank(message = "请填写做了什么") @Size(max = 2000, message = "不超过 2000 字") String content,
        @Size(max = 500, message = "不超过 500 字") String blockerText,
        @Size(max = 500, message = "不超过 500 字") String nextStep,
        @NotBlank(message = "请选择记录类型") String resultType,
        LocalDate checkDate,
        boolean resolvedBlocker) {
}
