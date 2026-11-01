package io.tracelink.infra.importing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("import_mapping")
public class ImportMappingEntity {

    @TableId(type = IdType.AUTO)
    public Long id;
    public String templateSignature;
    public String mapping;
    public Integer hitCount;
    public LocalDateTime updatedAt;
}
