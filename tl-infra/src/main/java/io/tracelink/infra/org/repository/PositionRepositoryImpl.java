package io.tracelink.infra.org.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.tracelink.domain.org.model.Position;
import io.tracelink.domain.org.repository.PositionRepository;
import io.tracelink.infra.org.entity.PositionEntity;
import io.tracelink.infra.org.mapper.PositionMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PositionRepositoryImpl implements PositionRepository {

    private final PositionMapper positionMapper;

    public PositionRepositoryImpl(PositionMapper positionMapper) {
        this.positionMapper = positionMapper;
    }

    @Override
    public List<Position> findByDeptId(Long deptId) {
        return positionMapper.selectList(new LambdaQueryWrapper<PositionEntity>()
                        .eq(PositionEntity::getDeptId, deptId)
                        .orderByAsc(PositionEntity::getSortNo)
                        .orderByAsc(PositionEntity::getId))
                .stream().map(PositionRepositoryImpl::toModel).toList();
    }

    @Override
    public List<Position> findAll() {
        return positionMapper.selectList(new LambdaQueryWrapper<PositionEntity>()
                        .orderByAsc(PositionEntity::getDeptId)
                        .orderByAsc(PositionEntity::getSortNo))
                .stream().map(PositionRepositoryImpl::toModel).toList();
    }

    @Override
    public Position findById(Long id) {
        PositionEntity entity = positionMapper.selectById(id);
        return entity == null ? null : toModel(entity);
    }

    @Override
    public boolean existsCode(Long deptId, String code, Long excludeId) {
        LambdaQueryWrapper<PositionEntity> wrapper = new LambdaQueryWrapper<PositionEntity>()
                .eq(PositionEntity::getDeptId, deptId)
                .eq(PositionEntity::getCode, code);
        if (excludeId != null) {
            wrapper.ne(PositionEntity::getId, excludeId);
        }
        return positionMapper.selectCount(wrapper) > 0;
    }

    @Override
    public void insert(Position position) {
        PositionEntity entity = toEntity(position);
        entity.setId(null);
        positionMapper.insert(entity);
        position.setId(entity.getId());
    }

    @Override
    public void update(Position position) {
        PositionEntity entity = toEntity(position);
        positionMapper.update(null, new LambdaUpdateWrapper<PositionEntity>()
                .eq(PositionEntity::getId, position.getId())
                .set(PositionEntity::getDeptId, entity.getDeptId())
                .set(PositionEntity::getCode, entity.getCode())
                .set(PositionEntity::getName, entity.getName())
                .set(PositionEntity::getJobLevel, entity.getJobLevel())
                .set(PositionEntity::getIsManager, entity.getIsManager())
                .set(PositionEntity::getSortNo, entity.getSortNo())
                .set(PositionEntity::getStatus, entity.getStatus()));
    }

    @Override
    public void deleteById(Long id) {
        positionMapper.deleteById(id);
    }

    public static Position toModel(PositionEntity e) {
        Position p = new Position();
        p.setId(e.getId());
        p.setDeptId(e.getDeptId());
        p.setCode(e.getCode());
        p.setName(e.getName());
        p.setJobLevel(e.getJobLevel());
        p.setIsManager(e.getIsManager());
        p.setSortNo(e.getSortNo());
        p.setStatus(e.getStatus());
        return p;
    }

    private static PositionEntity toEntity(Position p) {
        PositionEntity e = new PositionEntity();
        e.setId(p.getId());
        e.setDeptId(p.getDeptId());
        e.setCode(p.getCode());
        e.setName(p.getName());
        e.setJobLevel(p.getJobLevel());
        e.setIsManager(p.getIsManager());
        e.setSortNo(p.getSortNo());
        e.setStatus(p.getStatus());
        return e;
    }
}
