package io.tracelink.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeptRequest(
        @NotBlank(message = "不能为空") @Size(max = 32, message = "不超过 32 字") String code,
        @NotBlank(message = "不能为空") @Size(max = 64, message = "不超过 64 字") String name,
        Long parentId,
        Long leaderEmployeeId,
        @Min(value = 0, message = "不能为负数") Integer headcount,
        Integer sortNo,
        Integer status) {
}
