package io.tracelink.infra.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("role")
public class AdminRoleEntity {

    @TableId(type = IdType.AUTO)
    public Long id;
    public String code;
    public String name;
    public String dataScopeType;
    public String remark;
}
