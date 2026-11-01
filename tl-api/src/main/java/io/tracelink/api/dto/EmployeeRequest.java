package io.tracelink.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record EmployeeRequest(
        @NotBlank(message = "不能为空") @Size(max = 32, message = "不超过 32 字") String empNo,
        @NotBlank(message = "不能为空") @Size(max = 32, message = "不超过 32 字") String name,
        @NotNull(message = "请选择所属部门") Long deptId,
        Long positionId,
        Long managerId,
        @Size(max = 32, message = "不超过 32 字") String phone,
        @Email(message = "格式不正确") @Size(max = 64, message = "不超过 64 字") String email,
        Integer status,
        LocalDate joinDate) {
}
