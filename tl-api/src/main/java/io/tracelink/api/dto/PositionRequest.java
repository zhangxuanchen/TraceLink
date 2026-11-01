package io.tracelink.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PositionRequest(
        @NotNull(message = "请选择所属部门") Long deptId,
        @NotBlank(message = "不能为空") @Size(max = 32, message = "不超过 32 字") String code,
        @NotBlank(message = "不能为空") @Size(max = 64, message = "不超过 64 字") String name,
        @Min(value = 1, message = "职级 1 到 9") @Max(value = 9, message = "职级 1 到 9") Integer jobLevel,
        Integer isManager,
        Integer sortNo,
        Integer status) {
}
