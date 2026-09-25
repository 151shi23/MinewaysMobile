package com.mineways;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 「导出选项」选择器三态（多选 / 单选 / 不选）的单元测试。
 *
 * <p>覆盖的是 {@code MainActivity.optRadioGroup()} 真正使用的那份逻辑
 * （{@link ExportOptionSelector}），因此界面上每一组的取值规则都被这些用例钉住：
 * 预选哪一项、点击后存什么、导出时发送什么。
 */
public class ExportOptionSelectorTest {

    // —— 面板里真实使用的 6 组取值（顺序与 MainActivity 一致）——
    private static final int[] MAT = {ExportOptionSelector.NONE, 0, 1, 2, 3, 4};
    private static final int[] ROTATE = {ExportOptionSelector.NONE, 0, 90, 180, 270};
    private static final int[] SCALE = {ExportOptionSelector.NONE, 0, 1, 2, 3};
    private static final int[] UNITS = {ExportOptionSelector.NONE, 0, 1, 2, 3};
    private static final int[] PHYSMAT = {ExportOptionSelector.NONE, 0, 1, 13};
    private static final int[] DIM = {ExportOptionSelector.NONE, 0, 1, 2};

    private static final int MAT_DEF = 4, ROTATE_DEF = 0, SCALE_DEF = 2, UNITS_DEF = 0,
            PHYSMAT_DEF = 1, DIM_DEF = 0;

    private static final int[][] ALL = {MAT, ROTATE, SCALE, UNITS, PHYSMAT, DIM};
    private static final int[] ALL_DEF = {MAT_DEF, ROTATE_DEF, SCALE_DEF, UNITS_DEF, PHYSMAT_DEF, DIM_DEF};

    @Test
    public void noneIndex_detectedOnlyWhenFirstItemIsNone() {
        for (int[] v : ALL) {
            assertEquals("第一项是 NONE 的组必须能「不选」", 0, ExportOptionSelector.noneIndex(v));
        }
        assertEquals(-1, ExportOptionSelector.noneIndex(new int[]{0, 1, 2}));
        assertEquals(-1, ExportOptionSelector.noneIndex(new int[0]));
        assertEquals(-1, ExportOptionSelector.noneIndex(null));
    }

    @Test
    public void noneIsAlwaysFirstAndUnique() {
        for (int[] v : ALL) {
            assertEquals(ExportOptionSelector.NONE, v[0]);
            int hits = 0;
            for (int x : v) {
                if (x == ExportOptionSelector.NONE) hits++;
            }
            assertEquals("NONE 只能出现一次（否则互斥会失效）", 1, hits);
        }
    }

    /** 单选：每个合法值都能选中，并且预选下标指向的就是那个值。 */
    @Test
    public void roundTrip_everyValueSelectsItself() {
        for (int g = 0; g < ALL.length; g++) {
            int[] values = ALL[g];
            for (int i = 0; i < values.length; i++) {
                int stored = values[i];
                int idx = ExportOptionSelector.indexForStored(values, stored, ALL_DEF[g]);
                assertEquals("值 " + stored + " 应预选第 " + i + " 项", i, idx);
                assertEquals("下标 " + idx + " 应还原成同一个值",
                        stored, ExportOptionSelector.valueAt(values, idx));
            }
        }
    }

    /** 不选：存 -1 时预选「不选」项，并且导出时回退到该组的默认值。 */
    @Test
    public void none_roundTripsAndFallsBackToDefaultAtExport() {
        for (int g = 0; g < ALL.length; g++) {
            int[] values = ALL[g];
            int idx = ExportOptionSelector.indexForStored(values, ExportOptionSelector.NONE, ALL_DEF[g]);
            assertEquals("存 -1 应预选「不选」项", 0, idx);
            assertEquals(ExportOptionSelector.NONE, ExportOptionSelector.valueAt(values, idx));
            assertTrue(ExportOptionSelector.isNoneSelected(ExportOptionSelector.NONE));
            assertEquals("不选 → 导出时用默认值",
                    ALL_DEF[g], ExportOptionSelector.valueOrDefault(ExportOptionSelector.NONE, ALL_DEF[g]));
        }
    }

    /** 正常值：导出时必须原样发送，不能被默认值覆盖。 */
    @Test
    public void realValues_areSentUnchanged() {
        assertEquals(90, ExportOptionSelector.valueOrDefault(90, 0));
        assertEquals(0, ExportOptionSelector.valueOrDefault(0, 4));      // 0 是合法值，不是"不选"
        assertEquals(13, ExportOptionSelector.valueOrDefault(13, 1));
        assertEquals(2, ExportOptionSelector.valueOrDefault(2, 0));
        assertFalse(ExportOptionSelector.isNoneSelected(0));
    }

    /** 容错：存储值/默认值都不在表里时（换版本、手改偏好），预选退到第一项而不是崩。 */
    @Test
    public void unknownStoredValue_fallsBackSafely() {
        // MAT = {NONE, 0, 1, 2, 3, 4}：默认值 4 位于下标 5（下标 0 是「不选」项）
        assertEquals(5, ExportOptionSelector.indexForStored(MAT, 99, MAT_DEF));      // 命中默认值
        assertEquals(0, ExportOptionSelector.indexForStored(MAT, 99, 98));          // 都不命中 → 第一项
        assertEquals(ExportOptionSelector.NONE, ExportOptionSelector.valueAt(MAT, -1));
        assertEquals(ExportOptionSelector.NONE, ExportOptionSelector.valueAt(MAT, 999));
        assertEquals(ExportOptionSelector.NONE, ExportOptionSelector.valueAt(null, 0));
        assertEquals(-1, ExportOptionSelector.indexForStored(new int[0], 1, 1));
    }

    /** 不支持的组（第一项不是 NONE）也不该被「不选」逻辑影响。 */
    @Test
    public void groupWithoutNoneOptionKeepsPlainSemantics() {
        int[] plain = {0, 1, 2};
        assertEquals(-1, ExportOptionSelector.noneIndex(plain));
        assertEquals(1, ExportOptionSelector.indexForStored(plain, 1, 0));
        assertEquals(2, ExportOptionSelector.valueAt(plain, 2));
    }
}
