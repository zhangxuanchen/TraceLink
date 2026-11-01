package io.tracelink.infra.org.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.tracelink.domain.org.repository.EmployeeAliasRepository;
import io.tracelink.infra.org.entity.EmployeeAliasEntity;
import io.tracelink.infra.org.mapper.EmployeeAliasMapper;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class EmployeeAliasRepositoryImpl implements EmployeeAliasRepository {

    private final EmployeeAliasMapper mapper;

    public EmployeeAliasRepositoryImpl(EmployeeAliasMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Map<String, Long> aliases() {
        List<EmployeeAliasEntity> list = mapper.selectList(new QueryWrapper<>());
        Map<String, Long> map = new HashMap<>();
        for (EmployeeAliasEntity e : list) {
            if (e.alias != null && e.employeeId != null) {
                map.put(e.alias, e.employeeId);
            }
        }
        return map;
    }
}
