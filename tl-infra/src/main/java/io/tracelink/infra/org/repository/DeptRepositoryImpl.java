package io.tracelink.infra.org.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.tracelink.domain.org.model.Dept;
import io.tracelink.domain.org.repository.DeptRepository;
import io.tracelink.infra.org.entity.DeptEntity;
import io.tracelink.infra.org.mapper.DeptMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DeptRepositoryImpl implements DeptRepository {

    private final DeptMapper deptMapper;

    public DeptRepositoryImpl(DeptMapper deptMapper) {
        this.deptMapper = deptMapper;
    }

    @Override
    public List<Dept> findAll() {
        LambdaQueryWrapper<DeptEntity> wrapper = new LambdaQueryWrapper<DeptEntity>()
                .orderByAsc(DeptEntity::getSortNo)
                .orderByAsc(DeptEntity::getId);
        return deptMapper.selectList(wrapper).stream().map(DeptRepositoryImpl::toModel).toList();
    }

    @Override
    public Dept findById(Long id) {
        DeptEntity entity = deptMapper.selectById(id);
        return entity == null ? null : toModel(entity);
    }

    @Override
    public boolean existsCode(String code, Long excludeId) {
        LambdaQueryWrapper<DeptEntity> wrapper = new LambdaQueryWrapper<DeptEntity>()
                .eq(DeptEntity::getCode, code);
        if (excludeId != null) {
            wrapper.ne(DeptEntity::getId, excludeId);
        }
        return deptMapper.selectCount(wrapper) > 0;
    }

    @Override
    public void insert(Dept dept) {
        DeptEntity entity = toEntity(dept);
        entity.setId(null);
        deptMapper.insert(entity);
        dept.setId(entity.getId());
    }

    @Override
    public void update(Dept dept) {
        DeptEntity entity = toEntity(dept);
        deptMapper.update(null, new LambdaUpdateWrapper<DeptEntity>()
                .eq(DeptEntity::getId, dept.getId())
                .set(DeptEntity::getParentId, entity.getParentId())
                .set(DeptEntity::getCode, entity.getCode())
                .set(DeptEntity::getName, entity.getName())
                .set(DeptEntity::getPath, entity.getPath())
                .set(DeptEntity::getLevel, entity.getLevel())
                .set(DeptEntity::getLeaderEmployeeId, entity.getLeaderEmployeeId())
                .set(DeptEntity::getHeadcount, entity.getHeadcount())
                .set(DeptEntity::getSortNo, entity.getSortNo())
                .set(DeptEntity::getStatus, entity.getStatus()));
    }

    @Override
    public void deleteById(Long id) {
        deptMapper.deleteById(id);
    }

    @Override
    public int countChildren(Long id) {
        return Math.toIntExact(deptMapper.selectCount(
                new LambdaQueryWrapper<DeptEntity>().eq(DeptEntity::getParentId, id)));
    }

    public static Dept toModel(DeptEntity e) {
        Dept d = new Dept();
        d.setId(e.getId());
        d.setParentId(e.getParentId());
        d.setCode(e.getCode());
        d.setName(e.getName());
        d.setPath(e.getPath());
        d.setLevel(e.getLevel());
        d.setLeaderEmployeeId(e.getLeaderEmployeeId());
        d.setHeadcount(e.getHeadcount());
        d.setSortNo(e.getSortNo());
        d.setStatus(e.getStatus());
        return d;
    }

    private static DeptEntity toEntity(Dept d) {
        DeptEntity e = new DeptEntity();
        e.setId(d.getId());
        e.setParentId(d.getParentId());
        e.setCode(d.getCode());
        e.setName(d.getName());
        e.setPath(d.getPath());
        e.setLevel(d.getLevel());
        e.setLeaderEmployeeId(d.getLeaderEmployeeId());
        e.setHeadcount(d.getHeadcount());
        e.setSortNo(d.getSortNo());
        e.setStatus(d.getStatus());
        return e;
    }
}
