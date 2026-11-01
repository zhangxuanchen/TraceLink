package io.tracelink.infra.gate.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.tracelink.domain.gate.model.GateRule;
import io.tracelink.domain.gate.repository.GateRuleRepository;
import io.tracelink.infra.gate.entity.GateRuleEntity;
import io.tracelink.infra.gate.mapper.GateRuleMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class GateRuleRepositoryImpl implements GateRuleRepository {

    private final GateRuleMapper mapper;

    public GateRuleRepositoryImpl(GateRuleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<GateRule> findAll() {
        return mapper.selectList(new QueryWrapper<GateRuleEntity>()
                        .orderByAsc("sort_no")
                        .orderByAsc("id"))
                .stream().map(GateRuleRepositoryImpl::toModel).toList();
    }

    public static GateRule toModel(GateRuleEntity e) {
        GateRule r = new GateRule();
        r.id = e.id;
        r.taskType = e.taskType;
        r.gateCode = e.gateCode;
        r.weight = e.weight;
        r.required = e.required;
        r.holderRole = e.holderRole;
        r.sortNo = e.sortNo;
        r.enabled = e.enabled;
        return r;
    }
}
