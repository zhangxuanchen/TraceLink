package io.tracelink.infra.org.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色与数据范围按人读取。范围取该人所有角色里最宽的一个。
 */
public interface RoleMapper {

    @Select("SELECT r.code FROM user_role ur JOIN role r ON r.id = ur.role_id WHERE ur.employee_id = #{employeeId}")
    List<String> selectRoleCodes(@Param("employeeId") Long employeeId);

    @Select("""
            SELECT r.data_scope_type
            FROM user_role ur JOIN role r ON r.id = ur.role_id
            WHERE ur.employee_id = #{employeeId}
            ORDER BY FIELD(r.data_scope_type, 'ALL', 'DEPT_TREE', 'DEPT', 'SELF')
            LIMIT 1
            """)
    String selectWidestDataScope(@Param("employeeId") Long employeeId);
}
