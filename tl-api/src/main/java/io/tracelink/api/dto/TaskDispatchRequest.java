package io.tracelink.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record TaskDispatchRequest(
        Long primaryAssigneeId,
        List<Long> coAssigneeIds,
        LocalDateTime planEnd,
        @Min(value = 0, message = "只能取 0–3") @Max(value = 3, message = "只能取 0–3") Integer priority,
        @Size(max = 500, message = "不超过 500 字") String note) {
}
