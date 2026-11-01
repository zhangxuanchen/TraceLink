package io.tracelink.infra.org.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.tracelink.common.api.PageResult;
import io.tracelink.domain.org.model.Employee;
import io.tracelink.domain.org.repository.EmployeeRepository;
import io.tracelink.infra.org.entity.EmployeeEntity;
import io.tracelink.infra.org.mapper.EmployeeMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class EmployeeRepositoryImpl implements EmployeeRepository {

    private final EmployeeMapper employeeMapper;

    public EmployeeRepositoryImpl(EmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
    }

    @Override
    public PageResult<Employee> page(String keyword, List<Long> deptIds, Integer status, int pageNo, int pageSize) {
        LambdaQueryWrapper<EmployeeEntity> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(EmployeeEntity::getName, kw).or().like(EmployeeEntity::getEmpNo, kw));
        }
        if (deptIds != null && !deptIds.isEmpty()) {
            wrapper.in(EmployeeEntity::getDeptId, deptIds);
        }
        if (status != null) {
            wrapper.eq(EmployeeEntity::getStatus, status);
        }
        wrapper.orderByAsc(EmployeeEntity::getEmpNo);
        Page<EmployeeEntity> page = employeeMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<Employee> records = page.getRecords().stream().map(EmployeeRepositoryImpl::toModel).toList();
        return new PageResult<>(page.getTotal(), pageNo, pageSize, records);
    }

    @Override
    public List<Employee> findAll() {
        return employeeMapper.selectList(new LambdaQueryWrapper<EmployeeEntity>()
                        .orderByAsc(EmployeeEntity::getEmpNo))
                .stream().map(EmployeeRepositoryImpl::toModel).toList();
    }

    @Override
    public Employee findById(Long id) {
        EmployeeEntity entity = employeeMapper.selectById(id);
        return entity == null ? null : toModel(entity);
    }

    @Override
    public boolean existsEmpNo(String empNo, Long excludeId) {
        LambdaQueryWrapper<EmployeeEntity> wrapper = new LambdaQueryWrapper<EmployeeEntity>()
                .eq(EmployeeEntity::getEmpNo, empNo);
        if (excludeId != null) {
            wrapper.ne(EmployeeEntity::getId, excludeId);
        }
        return employeeMapper.selectCount(wrapper) > 0;
    }

    @Override
    public void insert(Employee employee) {
        EmployeeEntity entity = toEntity(employee);
        entity.setId(null);
        employeeMapper.insert(entity);
        employee.setId(entity.getId());
    }

    @Override
    public void update(Employee employee) {
        EmployeeEntity entity = toEntity(employee);
        employeeMapper.update(null, new LambdaUpdateWrapper<EmployeeEntity>()
                .eq(EmployeeEntity::getId, employee.getId())
                .set(EmployeeEntity::getEmpNo, entity.getEmpNo())
                .set(EmployeeEntity::getName, entity.getName())
                .set(EmployeeEntity::getDeptId, entity.getDeptId())
                .set(EmployeeEntity::getPositionId, entity.getPositionId())
                .set(EmployeeEntity::getManagerId, entity.getManagerId())
                .set(EmployeeEntity::getPhone, entity.getPhone())
                .set(EmployeeEntity::getEmail, entity.getEmail())
                .set(EmployeeEntity::getStatus, entity.getStatus())
                .set(EmployeeEntity::getJoinDate, entity.getJoinDate())
                .set(EmployeeEntity::getLeaveDate, entity.getLeaveDate()));
    }

    @Override
    public int countByDept(Long deptId) {
        return Math.toIntExact(employeeMapper.selectCount(
                new LambdaQueryWrapper<EmployeeEntity>().eq(EmployeeEntity::getDeptId, deptId)));
    }

    @Override
    public int countByPosition(Long positionId) {
        return Math.toIntExact(employeeMapper.selectCount(
                new LambdaQueryWrapper<EmployeeEntity>().eq(EmployeeEntity::getPositionId, positionId)));
    }

    public static Employee toModel(EmployeeEntity e) {
        Employee m = new Employee();
        m.setId(e.getId());
        m.setEmpNo(e.getEmpNo());
        m.setName(e.getName());
        m.setDeptId(e.getDeptId());
        m.setPositionId(e.getPositionId());
        m.setManagerId(e.getManagerId());
        m.setPhone(e.getPhone());
        m.setEmail(e.getEmail());
        m.setStatus(e.getStatus());
        m.setJoinDate(e.getJoinDate());
        m.setLeaveDate(e.getLeaveDate());
        return m;
    }

    private static EmployeeEntity toEntity(Employee m) {
        EmployeeEntity e = new EmployeeEntity();
        e.setId(m.getId());
        e.setEmpNo(m.getEmpNo());
        e.setName(m.getName());
        e.setDeptId(m.getDeptId());
        e.setPositionId(m.getPositionId());
        e.setManagerId(m.getManagerId());
        e.setPhone(m.getPhone());
        e.setEmail(m.getEmail());
        e.setStatus(m.getStatus());
        e.setJoinDate(m.getJoinDate());
        e.setLeaveDate(m.getLeaveDate());
        return e;
    }
}
