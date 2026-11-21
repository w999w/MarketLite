package io.github.marketlite;

/** 纯 Java 的配置校验逻辑。保持简单，便于桌面单元测试和后续版本兼容。 */
final class SettingsPolicy {
    static final String DEFAULT_ORDER = "01234567";
    static final int HOME_COUNT = 8;

    private SettingsPolicy() {}

    /** 排序必须是 0～7 的完整排列，不接受缺项、重复项或未知索引。 */
    static String normalizeOrder(String order) {
        if (order == null || order.length() != HOME_COUNT) return DEFAULT_ORDER;
        int seen = 0;
        for (int i = 0; i < HOME_COUNT; i++) {
            int index = order.charAt(i) - '0';
            if (index < 0 || index >= HOME_COUNT || (seen & (1 << index)) != 0) return DEFAULT_ORDER;
            seen |= 1 << index;
        }
        return order;
    }

    /** 0 表示商店默认；1～8 对应固定的标签标识，而不是当前显示位置。 */
    static int normalizeStart(int start) {
        return start >= 0 && start <= HOME_COUNT ? start : 0;
    }
}
