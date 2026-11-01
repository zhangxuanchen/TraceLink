package io.tracelink.domain.org.rules;

import io.tracelink.domain.org.model.Dept;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门树的组装与路径口径。路径形如 /1/2/5/，插入后由 id 生成，移动时整棵子树重算。
 */
public final class DeptTree {

    private DeptTree() {
    }

    public static List<Dept> assemble(List<Dept> all) {
        Map<Long, Dept> byId = new LinkedHashMap<>();
        for (Dept d : all) {
            d.setChildren(new ArrayList<>());
            byId.put(d.getId(), d);
        }
        List<Dept> roots = new ArrayList<>();
        for (Dept d : all) {
            Dept parent = byId.get(d.getParentId());
            if (parent == null) {
                roots.add(d);
            } else {
                parent.getChildren().add(d);
            }
        }
        Comparator<Dept> order = Comparator
                .comparing((Dept d) -> d.getSortNo() == null ? 0 : d.getSortNo())
                .thenComparing(Dept::getId);
        sortTree(roots, order);
        return roots;
    }

    private static void sortTree(List<Dept> nodes, Comparator<Dept> order) {
        nodes.sort(order);
        for (Dept node : nodes) {
            sortTree(node.getChildren(), order);
        }
    }

    public static String pathOf(String parentPath, Long id) {
        String base = parentPath == null || parentPath.isBlank() ? "/" : parentPath;
        return base.endsWith("/") ? base + id + "/" : base + "/" + id + "/";
    }

    public static boolean isSelfOrDescendant(String ancestorPath, String candidatePath) {
        return candidatePath != null && candidatePath.startsWith(ancestorPath);
    }
}
