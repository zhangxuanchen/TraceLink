package io.tracelink.app.org;

import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.org.model.Dept;
import io.tracelink.domain.org.repository.DeptRepository;
import io.tracelink.domain.org.repository.EmployeeRepository;
import io.tracelink.domain.org.rules.DeptTree;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DeptAppService {

    private final DeptRepository deptRepository;
    private final EmployeeRepository employeeRepository;

    public DeptAppService(DeptRepository deptRepository, EmployeeRepository employeeRepository) {
        this.deptRepository = deptRepository;
        this.employeeRepository = employeeRepository;
    }

    public List<Dept> tree() {
        return DeptTree.assemble(deptRepository.findAll());
    }

    @Transactional
    public Dept create(DeptCommand cmd) {
        validate(cmd);
        if (deptRepository.existsCode(cmd.code(), null)) {
            throw BizException.duplicate("部门编码已存在：" + cmd.code());
        }
        Dept parent = null;
        if (cmd.parentId() != null && cmd.parentId() > 0) {
            parent = deptRepository.findById(cmd.parentId());
            if (parent == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "上级部门不存在，请刷新后重试");
            }
        }
        Dept dept = new Dept();
        dept.setParentId(parent == null ? 0L : parent.getId());
        dept.setCode(cmd.code().trim());
        dept.setName(cmd.name().trim());
        dept.setLevel(parent == null ? 1 : parent.getLevel() + 1);
        dept.setLeaderEmployeeId(cmd.leaderEmployeeId());
        dept.setHeadcount(cmd.headcount());
        dept.setSortNo(cmd.sortNo() == null ? 0 : cmd.sortNo());
        dept.setStatus(cmd.status() == null ? 1 : cmd.status());
        dept.setPath("/");
        deptRepository.insert(dept);
        dept.setPath(DeptTree.pathOf(parent == null ? "/" : parent.getPath(), dept.getId()));
        deptRepository.update(dept);
        return dept;
    }

    @Transactional
    public Dept update(Long id, DeptCommand cmd) {
        validate(cmd);
        Dept existing = deptRepository.findById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "部门不存在，请刷新后重试");
        }
        if (deptRepository.existsCode(cmd.code(), id)) {
            throw BizException.duplicate("部门编码已存在：" + cmd.code());
        }
        Long newParentId = cmd.parentId() == null ? existing.getParentId() : cmd.parentId();
        Dept newParent = null;
        if (newParentId > 0) {
            newParent = deptRepository.findById(newParentId);
            if (newParent == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "上级部门不存在，请刷新后重试");
            }
            if (DeptTree.isSelfOrDescendant(existing.getPath(), newParent.getPath())) {
                throw new BizException(ErrorCode.RULE_UNSATISFIED, "不能把部门挂到它自己或它的下级");
            }
        }

        String oldPath = existing.getPath();
        String newPath = DeptTree.pathOf(newParent == null ? "/" : newParent.getPath(), id);
        int deltaLevel = (newParent == null ? 1 : newParent.getLevel() + 1) - existing.getLevel();

        existing.setParentId(newParentId);
        existing.setCode(cmd.code().trim());
        existing.setName(cmd.name().trim());
        existing.setLeaderEmployeeId(cmd.leaderEmployeeId());
        existing.setHeadcount(cmd.headcount());
        existing.setSortNo(cmd.sortNo() == null ? 0 : cmd.sortNo());
        existing.setStatus(cmd.status() == null ? 1 : cmd.status());
        existing.setPath(newPath);
        existing.setLevel(existing.getLevel() + deltaLevel);
        deptRepository.update(existing);

        if (deltaLevel != 0 || !oldPath.equals(newPath)) {
            for (Dept d : deptRepository.findAll()) {
                if (d.getId().equals(id) || !DeptTree.isSelfOrDescendant(oldPath, d.getPath())) {
                    continue;
                }
                d.setPath(newPath + d.getPath().substring(oldPath.length()));
                d.setLevel(d.getLevel() + deltaLevel);
                deptRepository.update(d);
            }
        }
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        Dept existing = deptRepository.findById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "部门不存在，请刷新后重试");
        }
        if (existing.getParentId() != null && existing.getParentId() == 0L) {
            throw new BizException(ErrorCode.RULE_UNSATISFIED, "根部门不能删除");
        }
        if (deptRepository.countChildren(id) > 0) {
            throw new BizException(ErrorCode.RULE_UNSATISFIED, "请先删除或移走下级部门");
        }
        if (employeeRepository.countByDept(id) > 0) {
            throw new BizException(ErrorCode.RULE_UNSATISFIED, "该部门还有人员，请先调整人员所属部门");
        }
        deptRepository.deleteById(id);
    }

    private void validate(DeptCommand cmd) {
        if (cmd.code() == null || cmd.code().isBlank()) {
            throw BizException.invalid("部门编码不能为空");
        }
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw BizException.invalid("部门名称不能为空");
        }
        if (cmd.name().length() > 64) {
            throw BizException.invalid("部门名称不超过 64 字");
        }
        if (cmd.headcount() != null && cmd.headcount() < 0) {
            throw BizException.invalid("编制人数不能为负数");
        }
    }
}
