package io.tracelink.infra.exporting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.tracelink.domain.exporting.model.ExportStatus;
import io.tracelink.domain.exporting.model.ExportTemplate;

import java.time.LocalDateTime;

@TableName("export_task")
public class ExportTaskEntity {

    @TableId(type = IdType.AUTO)
    public Long id;
    public ExportTemplate templateCode;
    public String params;
    public ExportStatus status;
    public String fileKey;
    public Integer rowCnt;
    public String errorMsg;
    public Long createdBy;
    public LocalDateTime createdAt;
}
