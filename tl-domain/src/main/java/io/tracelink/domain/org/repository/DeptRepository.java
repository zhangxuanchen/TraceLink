package io.tracelink.domain.org.repository;

import io.tracelink.domain.org.model.Dept;

import java.util.List;

public interface DeptRepository {

    List<Dept> findAll();

    Dept findById(Long id);

    boolean existsCode(String code, Long excludeId);

    void insert(Dept dept);

    void update(Dept dept);

    void deleteById(Long id);

    int countChildren(Long id);
}
