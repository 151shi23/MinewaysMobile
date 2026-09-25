package com.mineways;

/**
 * 「导出选项」选择器的纯逻辑（**不依赖任何 Android 类**，因此可用普通 JVM 单元测试覆盖）。
 *
 * <p>三态语义（见 README「导出选项的三态」）：
 * <ul>
 *   <li><b>多选</b>：开关组，互不影响（不是本类的职责）；</li>
 *   <li><b>单选</b>：单选组内互斥，值可任意（如旋转 0/90/180/270、物理材料 0/1/13）；</li>
 *   <li><b>不选</b>：每个单选组第一项固定为 {@link #NONE}，点已选中项也会回到它；
 *       导出时按该键的默认值发送 —— 与"从未设置过"完全等价。</li>
 * </ul>
 *
 * <p>{@code MainActivity.optRadioGroup()} 与 {@code ExportOptionSelectorTest} 共用这里的实现，
 * 保证"界面上看到的"与"单元测试验证的"是同一套规则。
 */
public final class ExportOptionSelector {

    /** 「不选（用默认）」的存储值。 */
    public static final int NONE = -1;

    private ExportOptionSelector() {
    }

    /**
     * 该组的「不选」项下标：仅当第一项是 {@link #NONE} 时返回 0；否则返回 -1（该组不支持不选）。
     */
    public static int noneIndex(int[] values) {
        return (values != null && values.length > 0 && values[0] == NONE) ? 0 : -1;
    }

    /**
     * 打开面板时应该预选哪一项：
     * <ol>
     *   <li>存储值命中某项 → 该项下标（{@link #NONE} 命中即「不选」项）；</li>
     *   <li>否则默认值命中某项 → 该项下标；</li>
     *   <li>再否则退到第一项。</li>
     * </ol>
     *
     * @return 下标；values 为空时返回 -1
     */
    public static int indexForStored(int[] values, int stored, int defValue) {
        if (values == null || values.length == 0) return -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == stored) return i;
        }
        for (int i = 0; i < values.length; i++) {
            if (values[i] == defValue) return i;
        }
        return 0;
    }

    /** 取下标对应的存储值；下标越界时返回 {@link #NONE}（调用方应忽略越界情形）。 */
    public static int valueAt(int[] values, int index) {
        if (values == null || index < 0 || index >= values.length) return NONE;
        return values[index];
    }

    /**
     * 导出时实际发送的值：为负（＝「不选」）时用默认值，等价于"从未设置"。
     * 这样即使用户把某一组设成"不选"，导出的行为也一定是可预期的。
     */
    public static int valueOrDefault(int stored, int defValue) {
        return stored < 0 ? defValue : stored;
    }

    /** 该存储值是否表示「不选」。 */
    public static boolean isNoneSelected(int stored) {
        return stored < 0;
    }
}
