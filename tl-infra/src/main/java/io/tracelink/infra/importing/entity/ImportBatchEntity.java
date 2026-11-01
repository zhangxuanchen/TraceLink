package io.tracelink.infra.importing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.tracelink.domain.importing.model.ImportStatus;

import java.time.LocalDateTime;

@TableName("import_batch")
public class ImportBatchEntity {

    @TableId(type = IdType.AUTO)
    public Long id;
    public String fileHash;
    public String fileName;
    public String fileKey;
    public String mode;
    public ImportStatus status;
    public Integer sheetTotal;
    public Integer rowTotal;
    public Integer rowOk;
    public Integer rowWarn;
    public Integer rowError;
    public String mapping;
    public String errorMsg;
    public Long createdBy;
    public LocalDateTime createdAt;
}
