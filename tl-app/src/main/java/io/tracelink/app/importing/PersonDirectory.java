package io.tracelink.app.importing;

import io.tracelink.domain.org.model.Employee;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 人名解析器。按 DESIGN 4.5 的顺序：别名 → 精确姓名 → 姓名前缀（取最长）→ 姓名包含。
 * 部门名、班组名等组织短语不进人员表，也不计入匹配率的分母。
 */
public class PersonDirectory {

    private final Map<String, Employee> byName = new LinkedHashMap<>();
    private final Map<String, Long> aliases;
    private final Map<Long, Employee> byId = new LinkedHashMap<>();
    private final List<Employee> sortedByLength = new ArrayList<>();
    /**
     * 组织与事务短语的停用词。这类串出现在责任人列里（如「各个部门负责人」「设备厂」「人事预算」），
     * 不是人，所以既不进人员表，也不计入人名匹配率的分母。
     */
    private final Set<String> orgWords = new LinkedHashSet<>(List.of(
            "办公室", "班组", "车间", "各部门", "各部", "各办", "各位", "全员", "全体", "公司", "厂",
            "部门", "负责人", "发言人", "供应商", "施工方", "厂家", "客户", "预算"));

    private PersonDirectory(Map<String, Long> aliases) {
        this.aliases = aliases;
    }

    public static PersonDirectory build(List<Employee> employees, Map<String, Long> aliases, List<String> deptNames) {
        PersonDirectory dir = new PersonDirectory(aliases == null ? Map.of() : aliases);
        for (Employee e : employees) {
            if (e.getName() == null || e.getName().isBlank()) {
                continue;
            }
            dir.byName.putIfAbsent(e.getName(), e);
            dir.byId.put(e.getId(), e);
        }
        dir.sortedByLength.addAll(dir.byName.values());
        dir.sortedByLength.sort(Comparator.comparingInt((Employee e) -> e.getName().length()).reversed());
        if (deptNames != null) {
            for (String name : deptNames) {
                if (name != null && !name.isBlank()) {
                    dir.orgWords.add(name);
                }
            }
        }
        return dir;
    }

    public Employee byId(Long id) {
        return id == null ? null : byId.get(id);
    }

    public Resolution resolve(String text) {
        List<Employee> matched = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return new Resolution(matched, unmatched, 0);
        }
        int mentions = 0;
        for (String token : tokens(text)) {
            if (token.isBlank() || token.matches("[0-9.\\-]+") || isOrgPhrase(token)) {
                continue;
            }
            mentions++;
            Employee employee = match(token);
            if (employee == null) {
                unmatched.add(token);
            } else if (!matched.contains(employee)) {
                matched.add(employee);
            }
        }
        return new Resolution(matched, unmatched, mentions);
    }

    private List<String> tokens(String text) {
        List<String> tokens = new ArrayList<>();
        for (String part : text.split("[、，,；;/\\s\\u3000]+")) {
            if (part.isBlank()) {
                continue;
            }
            if (part.length() > 3 && part.contains("和")) {
                for (String inner : part.split("和")) {
                    if (!inner.isBlank()) {
                        tokens.add(inner.trim());
                    }
                }
            } else {
                tokens.add(part.trim());
            }
        }
        return tokens;
    }

    private boolean isOrgPhrase(String token) {
        for (String word : orgWords) {
            if (token.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private Employee match(String token) {
        Long aliasId = aliases.get(token);
        if (aliasId != null && byId.containsKey(aliasId)) {
            return byId.get(aliasId);
        }
        Employee exact = byName.get(token);
        if (exact != null) {
            return exact;
        }
        for (Employee e : sortedByLength) {
            if (token.startsWith(e.getName())) {
                return e;
            }
        }
        for (Employee e : sortedByLength) {
            if (token.contains(e.getName())) {
                return e;
            }
        }
        return null;
    }

    public record Resolution(List<Employee> matched, List<String> unmatched, int mentions) {

        public Employee primary() {
            return matched.isEmpty() ? null : matched.get(0);
        }

        public List<Employee> others(Employee exclude) {
            List<Employee> rest = new ArrayList<>();
            for (Employee e : matched) {
                if (exclude == null || !e.getId().equals(exclude.getId())) {
                    rest.add(e);
                }
            }
            return rest;
        }
    }
}
