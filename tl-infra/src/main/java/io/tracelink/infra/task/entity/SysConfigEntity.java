package io.tracelink.infra.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_config")
public class SysConfigEntity {

    @TableId(type = IdType.AUTO)
    public Long id;
    public String cfgKey;
    public String cfgValue;
    public String valueType;
    public String groupCode;
    public String remark;
    public Long updatedBy;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
