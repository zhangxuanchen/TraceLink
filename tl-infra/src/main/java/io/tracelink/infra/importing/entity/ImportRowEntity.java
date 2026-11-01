package io.tracelink.infra.importing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.tracelink.domain.importing.model.RowLevel;

@TableName("import_row")
public class ImportRowEntity {

    @TableId(type = IdType.AUTO)
    public Long id;
    public Long batchId;
    public String sheet;
    public Integer rowNo;
    public String raw;
    public String parsed;
    public RowLevel level;
    public String issues;
    public Long taskId;
    public String fingerprint;
}
