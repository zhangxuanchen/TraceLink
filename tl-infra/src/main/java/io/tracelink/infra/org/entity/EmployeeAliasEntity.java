package io.tracelink.infra.org.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("employee_alias")
public class EmployeeAliasEntity {

    @TableId(type = IdType.AUTO)
    public Long id;
    public Long employeeId;
    public String alias;
    public String source;
}
