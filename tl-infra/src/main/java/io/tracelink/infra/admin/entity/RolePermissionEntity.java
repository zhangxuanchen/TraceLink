package io.tracelink.infra.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("role_permission")
public class RolePermissionEntity {

    @TableId(type = IdType.AUTO)
    public Long id;
    public Long roleId;
    public Long permissionId;
}
