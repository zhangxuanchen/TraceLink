package io.tracelink.domain.task.rules;

/** 逾期升级层级，对应 R5：当日主责、次日负责人、第三日负责人上级、第七日管理者逾期池 */
public enum OverdueLevel {

    NONE("未逾期", 0),
    ASSIGNEE("提醒主责", 1),
    OWNER("提醒负责人", 2),
    OWNER_MANAGER("提醒负责人上级", 3),
    MANAGER_POOL("管理者逾期池", 7);

    private final String label;
    private final int fromDay;

    OverdueLevel(String label, int fromDay) {
        this.label = label;
        this.fromDay = fromDay;
    }

    public String label() {
        return label;
    }

    public int fromDay() {
        return fromDay;
    }

    public static OverdueLevel of(int overdueDays) {
        if (overdueDays <= 0) {
            return NONE;
        }
        if (overdueDays < 2) {
            return ASSIGNEE;
        }
        if (overdueDays < 3) {
            return OWNER;
        }
        if (overdueDays < 7) {
            return OWNER_MANAGER;
        }
        return MANAGER_POOL;
    }
}
