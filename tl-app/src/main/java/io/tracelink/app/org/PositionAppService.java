package io.tracelink.app.org;

import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.org.model.Position;
import io.tracelink.domain.org.repository.DeptRepository;
import io.tracelink.domain.org.repository.EmployeeRepository;
import io.tracelink.domain.org.repository.PositionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PositionAppService {

    private final PositionRepository positionRepository;
    private final DeptRepository deptRepository;
    private final EmployeeRepository employeeRepository;

    public PositionAppService(PositionRepository positionRepository,
                              DeptRepository deptRepository,
                              EmployeeRepository employeeRepository) {
        this.positionRepository = positionRepository;
        this.deptRepository = deptRepository;
        this.employeeRepository = employeeRepository;
    }

    public List<Position> list(Long deptId) {
        return deptId == null ? positionRepository.findAll() : positionRepository.findByDeptId(deptId);
    }

    @Transactional
    public Position create(PositionCommand cmd) {
        validate(cmd);
        if (deptRepository.findById(cmd.deptId()) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "所属部门不存在，请刷新后重试");
        }
        if (positionRepository.existsCode(cmd.deptId(), cmd.code(), null)) {
            throw BizException.duplicate("该部门下职位编码已存在：" + cmd.code());
        }
        Position position = new Position();
        apply(position, cmd);
        positionRepository.insert(position);
        return position;
    }

    @Transactional
    public Position update(Long id, PositionCommand cmd) {
        validate(cmd);
        Position existing = positionRepository.findById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "职位不存在，请刷新后重试");
        }
        if (positionRepository.existsCode(cmd.deptId(), cmd.code(), id)) {
            throw BizException.duplicate("该部门下职位编码已存在：" + cmd.code());
        }
        apply(existing, cmd);
        positionRepository.update(existing);
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        Position existing = positionRepository.findById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "职位不存在，请刷新后重试");
        }
        if (employeeRepository.countByPosition(id) > 0) {
            throw new BizException(ErrorCode.RULE_UNSATISFIED, "该职位还有人员，请先调整人员职位");
        }
        positionRepository.deleteById(id);
    }

    private void apply(Position position, PositionCommand cmd) {
        position.setDeptId(cmd.deptId());
        position.setCode(cmd.code().trim());
        position.setName(cmd.name().trim());
        position.setJobLevel(cmd.jobLevel() == null ? 1 : cmd.jobLevel());
        position.setIsManager(cmd.isManager() == null ? 0 : cmd.isManager());
        position.setSortNo(cmd.sortNo() == null ? 0 : cmd.sortNo());
        position.setStatus(cmd.status() == null ? 1 : cmd.status());
    }

    private void validate(PositionCommand cmd) {
        if (cmd.deptId() == null) {
            throw BizException.invalid("请选择所属部门");
        }
        if (cmd.code() == null || cmd.code().isBlank()) {
            throw BizException.invalid("职位编码不能为空");
        }
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw BizException.invalid("职位名称不能为空");
        }
        if (cmd.jobLevel() != null && (cmd.jobLevel() < 1 || cmd.jobLevel() > 9)) {
            throw BizException.invalid("职级只能是 1 到 9");
        }
    }
}
