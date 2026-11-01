package io.tracelink.app.kb;

import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.kb.model.KbScope;
import io.tracelink.domain.org.model.Dept;
import io.tracelink.domain.org.repository.DeptRepository;
import io.tracelink.domain.org.rules.DeptTree;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 把账号的数据范围换算成知识检索范围。
 * ALL 不按部门收窄；DEPT_TREE 取本部门及下级；DEPT 与 SELF 只取本部门。
 * 全员可见的文档不受部门列表限制，由仓储层的 visibility='ALL' 分支负责。
 */
@Service
public class KbScopeResolver {

    private final DeptRepository deptRepository;

    public KbScopeResolver(DeptRepository deptRepository) {
        this.deptRepository = deptRepository;
    }

    public KbScope resolve(AuthUser user) {
        String type = user.dataScopeType() == null ? "SELF" : user.dataScopeType();
        if ("ALL".equals(type)) {
            return KbScope.unrestricted();
        }
        List<Dept> depts = deptRepository.findAll();
        Dept mine = depts.stream()
                .filter(d -> Objects.equals(d.getId(), user.deptId()))
                .findFirst()
                .orElse(null);
        if (mine == null) {
            return new KbScope(false, List.of());
        }
        if ("DEPT_TREE".equals(type)) {
            return new KbScope(false, depts.stream()
                    .filter(d -> d.getPath() != null && mine.getPath() != null
                            && DeptTree.isSelfOrDescendant(mine.getPath(), d.getPath()))
                    .map(Dept::getId)
                    .toList());
        }
        return new KbScope(false, List.of(mine.getId()));
    }
}
