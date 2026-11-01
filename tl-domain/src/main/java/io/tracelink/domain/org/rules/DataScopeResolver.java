package io.tracelink.domain.org.rules;

import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.org.model.Dept;
import io.tracelink.domain.task.model.TaskScope;

import java.util.List;

/**
 * 数据范围换算。ALL 不收窄；DEPT_TREE 取本部门及下级；DEPT 只取本部门；其余按 SELF 只取与本人有关的工单。
 */
public final class DataScopeResolver {

    private DataScopeResolver() {
    }

    public static TaskScope resolve(AuthUser user, List<Dept> depts) {
        String type = user.dataScopeType() == null ? "SELF" : user.dataScopeType();
        if ("ALL".equals(type)) {
            return TaskScope.all(user.employeeId());
        }
        Dept mine = depts.stream()
                .filter(d -> d.getId().equals(user.deptId()))
                .findFirst()
                .orElse(null);
        if (mine == null) {
            return new TaskScope(null, user.employeeId(), true);
        }
        if ("DEPT_TREE".equals(type)) {
            List<Long> ids = depts.stream()
                    .filter(d -> DeptTree.isSelfOrDescendant(mine.getPath(), d.getPath()))
                    .map(Dept::getId)
                    .toList();
            return new TaskScope(ids, user.employeeId(), false);
        }
        if ("DEPT".equals(type)) {
            return new TaskScope(List.of(mine.getId()), user.employeeId(), false);
        }
        return new TaskScope(null, user.employeeId(), true);
    }
}
