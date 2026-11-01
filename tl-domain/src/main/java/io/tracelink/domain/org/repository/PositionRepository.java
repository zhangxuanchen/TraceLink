package io.tracelink.domain.org.repository;

import io.tracelink.domain.org.model.Position;

import java.util.List;

public interface PositionRepository {

    List<Position> findByDeptId(Long deptId);

    List<Position> findAll();

    Position findById(Long id);

    boolean existsCode(Long deptId, String code, Long excludeId);

    void insert(Position position);

    void update(Position position);

    void deleteById(Long id);
}
