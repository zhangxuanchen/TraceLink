package io.tracelink.infra.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("permission")
public class PermissionEntity {

    @TableId(type = IdType.AUTO)
    public Long id;
    public String code;
    public String name;
    public String module;
}
