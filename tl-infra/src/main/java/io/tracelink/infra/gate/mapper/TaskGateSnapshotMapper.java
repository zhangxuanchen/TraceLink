package io.tracelink.infra.gate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.tracelink.infra.gate.entity.TaskGateSnapshotEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TaskGateSnapshotMapper extends BaseMapper<TaskGateSnapshotEntity> {

    /** 批量写快照；一次五百行，避免单条循环在万级工单上耗时 */
    @Insert("<script>INSERT INTO task_gate_snapshot"
            + " (snap_date, task_id, dept_id, task_type, gate_code, state, gap_days, holder_id) VALUES "
            + "<foreach collection='rows' item='r' separator=','>"
            + "(#{r.snapDate}, #{r.taskId}, #{r.deptId}, #{r.taskType}, #{r.gateCode}, #{r.state}, #{r.gapDays}, #{r.holderId})"
            + "</foreach></script>")
    int insertBatch(@Param("rows") List<TaskGateSnapshotEntity> rows);
}
