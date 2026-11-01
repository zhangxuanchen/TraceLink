package io.tracelink.app.org;

import io.tracelink.common.api.PageResult;
import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.org.model.Dept;
import io.tracelink.domain.org.model.Employee;
import io.tracelink.domain.org.model.Position;
import io.tracelink.domain.org.repository.DeptRepository;
import io.tracelink.domain.org.repository.EmployeeAliasRepository;
import io.tracelink.domain.org.repository.EmployeeRepository;
import io.tracelink.domain.org.repository.PositionRepository;
import io.tracelink.domain.org.rules.DeptTree;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EmployeeAppService {

    private final EmployeeRepository employeeRepository;
    private final DeptRepository deptRepository;
    private final PositionRepository positionRepository;
    private final EmployeeAliasRepository aliasRepository;

    public EmployeeAppService(EmployeeRepository employeeRepository,
                              DeptRepository deptRepository,
                              PositionRepository positionRepository,
                              EmployeeAliasRepository aliasRepository) {
        this.employeeRepository = employeeRepository;
        this.deptRepository = deptRepository;
        this.positionRepository = positionRepository;
        this.aliasRepository = aliasRepository;
    }

    public PageResult<EmployeeView> page(String keyword, Long deptId, Integer status, int pageNo, int pageSize) {
        List<Long> deptIds = null;
        if (deptId != null) {
            Dept target = deptRepository.findById(deptId);
            if (target == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "部门不存在，请刷新后重试");
            }
            deptIds = deptRepository.findAll().stream()
                    .filter(d -> DeptTree.isSelfOrDescendant(target.getPath(), d.getPath()))
                    .map(Dept::getId)
                    .toList();
        }
        PageResult<Employee> page = employeeRepository.page(keyword, deptIds, status, pageNo, pageSize);
        Map<Long, String> deptNames = deptRepository.findAll().stream()
                .collect(Collectors.toMap(Dept::getId, Dept::getName));
        Map<Long, String> positionNames = positionRepository.findAll().stream()
                .collect(Collectors.toMap(Position::getId, Position::getName));
        Map<Long, String> employeeNames = employeeRepository.findAll().stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getName));
        List<EmployeeView> views = page.records().stream()
                .map(e -> toView(e, deptNames, positionNames, employeeNames))
                .toList();
        return new PageResult<>(page.total(), page.pageNo(), page.pageSize(), views);
    }

    public EmployeeView detail(Long id) {
        Employee employee = employeeRepository.findById(id);
        if (employee == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "人员不存在，请刷新后重试");
        }
        return toView(employee, deptNameMap(), positionNameMap(), employeeNameMap());
    }

    @Transactional
    public EmployeeView create(EmployeeCommand cmd) {
        validate(cmd, null);
        if (employeeRepository.existsEmpNo(cmd.empNo(), null)) {
            throw BizException.duplicate("工号已存在：" + cmd.empNo());
        }
        Employee employee = new Employee();
        apply(employee, cmd);
        employeeRepository.insert(employee);
        return detail(employee.getId());
    }

    @Transactional
    public EmployeeView update(Long id, EmployeeCommand cmd) {
        Employee existing = employeeRepository.findById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "人员不存在，请刷新后重试");
        }
        validate(cmd, id);
        if (employeeRepository.existsEmpNo(cmd.empNo(), id)) {
            throw BizException.duplicate("工号已存在：" + cmd.empNo());
        }
        apply(existing, cmd);
        employeeRepository.update(existing);
        return detail(id);
    }

    public List<EmployeeView> options() {
        Map<Long, String> deptNames = deptNameMap();
        Map<Long, String> positionNames = positionNameMap();
        Map<Long, String> employeeNames = employeeNameMap();
        return employeeRepository.findAll().stream()
                .map(e -> toView(e, deptNames, positionNames, employeeNames))
                .toList();
    }

    /** ORG-3 姓名解析：导出的日报、智能会话里出现的人名都走这里落成员工 */
    public List<EmployeeView> resolve(String name) {
        List<EmployeeView> all = options();
        if (name == null || name.isBlank()) {
            return List.of();
        }
        String keyword = name.trim();
        List<EmployeeView> exact = all.stream()
                .filter(e -> keyword.equals(e.name()))
                .toList();
        if (!exact.isEmpty()) {
            return exact;
        }
        // 别名与本人名同权：样本里「张总」与「张厚生」是同一个人，导入与建议都靠这条解析
        Long aliasId = aliasRepository.aliases().get(keyword);
        if (aliasId != null) {
            List<EmployeeView> byAlias = all.stream()
                    .filter(e -> aliasId.equals(e.id()))
                    .toList();
            if (!byAlias.isEmpty()) {
                return byAlias;
            }
        }
        return all.stream()
                .filter(e -> e.name() != null && (e.name().contains(keyword) || keyword.contains(e.name())))
                .limit(5)
                .toList();
    }

    private void validate(EmployeeCommand cmd, Long excludeId) {
        if (cmd.empNo() == null || cmd.empNo().isBlank()) {
            throw BizException.invalid("工号不能为空");
        }
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw BizException.invalid("姓名不能为空");
        }
        if (cmd.deptId() == null) {
            throw BizException.invalid("请选择所属部门");
        }
        if (deptRepository.findById(cmd.deptId()) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "所属部门不存在，请刷新后重试");
        }
        if (cmd.positionId() != null) {
            Position position = positionRepository.findById(cmd.positionId());
            if (position == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "职位不存在，请刷新后重试");
            }
            if (!Objects.equals(position.getDeptId(), cmd.deptId())) {
                throw new BizException(ErrorCode.RULE_UNSATISFIED, "所选职位不属于该部门");
            }
        }
        if (cmd.managerId() != null) {
            if (excludeId != null && excludeId.equals(cmd.managerId())) {
                throw new BizException(ErrorCode.RULE_UNSATISFIED, "直属上级不能是本人");
            }
            if (employeeRepository.findById(cmd.managerId()) == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "直属上级不存在，请刷新后重试");
            }
        }
        if (cmd.status() != null && cmd.status() != 1 && cmd.status() != 2 && cmd.status() != 3) {
            throw BizException.invalid("人员状态只能是 1 在职、2 离职、3 试用");
        }
    }

    private void apply(Employee employee, EmployeeCommand cmd) {
        employee.setEmpNo(cmd.empNo().trim());
        employee.setName(cmd.name().trim());
        employee.setDeptId(cmd.deptId());
        employee.setPositionId(cmd.positionId());
        employee.setManagerId(cmd.managerId());
        employee.setPhone(cmd.phone());
        employee.setEmail(cmd.email());
        employee.setStatus(cmd.status() == null ? 1 : cmd.status());
        employee.setJoinDate(cmd.joinDate());
        if (cmd.status() != null && cmd.status() == 2) {
            employee.setLeaveDate(java.time.LocalDate.now());
        } else if (cmd.status() != null) {
            employee.setLeaveDate(null);
        }
    }

    private EmployeeView toView(Employee e, Map<Long, String> deptNames,
                                Map<Long, String> positionNames, Map<Long, String> employeeNames) {
        return new EmployeeView(
                e.getId(),
                e.getEmpNo(),
                e.getName(),
                e.getDeptId(),
                deptNames.get(e.getDeptId()),
                e.getPositionId(),
                e.getPositionId() == null ? null : positionNames.get(e.getPositionId()),
                e.getManagerId(),
                e.getManagerId() == null ? null : employeeNames.get(e.getManagerId()),
                e.getPhone(),
                e.getEmail(),
                e.getStatus(),
                e.getJoinDate());
    }

    private Map<Long, String> deptNameMap() {
        return deptRepository.findAll().stream().collect(Collectors.toMap(Dept::getId, Dept::getName));
    }

    private Map<Long, String> positionNameMap() {
        return positionRepository.findAll().stream()
                .collect(Collectors.toMap(Position::getId, Position::getName));
    }

    private Map<Long, String> employeeNameMap() {
        return employeeRepository.findAll().stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getName));
    }
}
