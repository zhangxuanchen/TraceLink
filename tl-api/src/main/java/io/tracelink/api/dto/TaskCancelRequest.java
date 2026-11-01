package io.tracelink.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskCancelRequest(
        @NotBlank(message = "请填写取消原因") @Size(max = 1000, message = "不超过 1000 字") String reason) {
}
