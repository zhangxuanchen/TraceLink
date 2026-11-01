package io.tracelink.domain.org.repository;

import io.tracelink.common.api.PageResult;
import io.tracelink.domain.org.model.Employee;

import java.util.List;

public interface EmployeeRepository {

    PageResult<Employee> page(String keyword, List<Long> deptIds, Integer status, int pageNo, int pageSize);

    List<Employee> findAll();

    Employee findById(Long id);

    boolean existsEmpNo(String empNo, Long excludeId);

    void insert(Employee employee);

    void update(Employee employee);

    int countByDept(Long deptId);

    int countByPosition(Long positionId);
}
