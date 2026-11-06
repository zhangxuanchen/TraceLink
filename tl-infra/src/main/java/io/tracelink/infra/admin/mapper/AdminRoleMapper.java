package io.tracelink.infra.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.tracelink.infra.admin.entity.AdminRoleEntity;
import io.tracelink.infra.admin.entity.PermissionEntity;
import io.tracelink.infra.admin.entity.RolePermissionEntity;
import io.tracelink.infra.admin.entity.UserRoleEntity;

/** 设置页用到的四张表；账号与人员复用 org 模块的 Mapper */
public interface AdminRoleMapper extends BaseMapper<AdminRoleEntity> {
}
