package io.tracelink.infra.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("user_role")
public class UserRoleEntity {

    @TableId(type = IdType.AUTO)
    public Long id;
    public Long employeeId;
    public Long roleId;
}
