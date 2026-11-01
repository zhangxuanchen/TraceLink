package io.tracelink.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TaskCreateRequest(
        @NotBlank(message = "不能为空") @Size(min = 2, max = 120, message = "长度为 2–120 字") String title,
        @Size(max = 5000, message = "不超过 5000 字") String description,
        @NotBlank(message = "请选择工单类型") String type,
        String sourceType,
        @NotNull(message = "请选择承办部门") Long deptId,
        @NotNull(message = "请选择负责人") Long ownerId,
        @NotNull(message = "请选择主责执行人") Long primaryAssigneeId,
        List<Long> coAssigneeIds,
        @Size(max = 128, message = "不超过 128 字") String assigneeText,
        @Min(value = 0, message = "只能取 0–3") @Max(value = 3, message = "只能取 0–3") Integer priority,
        LocalDateTime planStart,
        @NotNull(message = "请填写完成期限") LocalDateTime planEnd,
        List<@Size(max = 200, message = "单条不超过 200 字") String> acceptCriteria,
        List<@Size(max = 500, message = "单条不超过 500 字") String> issueList,
        List<ActionInput> actionList,
        @Size(max = 128, message = "不超过 128 字") String tags) {

    public record ActionInput(
            @NotBlank(message = "不能为空") @Size(max = 500, message = "不超过 500 字") String content,
            Long ownerId,
            LocalDate dueDate) {
    }
}
