package io.tracelink.infra.gate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.tracelink.infra.gate.entity.GateGapDailyEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface GateGapDailyMapper extends BaseMapper<GateGapDailyEntity> {

    @Insert("<script>INSERT INTO gate_gap_daily"
            + " (snap_date, dept_id, gate_code, done_count, todo_count, na_count, max_gap_days) VALUES "
            + "<foreach collection='rows' item='r' separator=','>"
            + "(#{r.snapDate}, #{r.deptId}, #{r.gateCode}, #{r.doneCount}, #{r.todoCount}, #{r.naCount}, #{r.maxGapDays})"
            + "</foreach></script>")
    int insertBatch(@Param("rows") List<GateGapDailyEntity> rows);
}
