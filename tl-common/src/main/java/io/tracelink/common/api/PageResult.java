package io.tracelink.common.api;

import java.util.List;

public record PageResult<T>(long total, int pageNo, int pageSize, List<T> records) {

    public static <T> PageResult<T> empty(int pageNo, int pageSize) {
        return new PageResult<>(0L, pageNo, pageSize, List.of());
    }
}
