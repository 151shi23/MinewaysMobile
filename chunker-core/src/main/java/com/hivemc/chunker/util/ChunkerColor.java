package com.hivemc.chunker.util;

/**
 * A simple immutable RGBA color used in place of {@code java.awt.Color}.
 * <p>
 * Android 的运行时里没有 {@code java.awt} / {@code javax.imageio}（{@code java/awt} 下只有
 * {@code font.NumericShaper} 等少数类，{@code java.awt.Color} 并不存在），而本引擎原先在
 * 物品显示、染料匹配、烟花颜色、地图调色板等路径上直接使用了 {@code java.awt.Color}，
 * 在手机上会抛 {@code NoClassDefFoundError: java/awt/Color}。
 * 这里用一个等价的内部类型替换，语义与 {@code java.awt.Color} 在原先用法下的行为一致：
 * <ul>
 *   <li>{@link #fromRGB(int)} 对应 {@code new Color(int rgb)}：红绿蓝取自低位 24 位，alpha 固定 255；</li>
 *   <li>{@link #fromRGB(int, int, int, int)} 对应 {@code new Color(int r, int g, int b, int a)}；</li>
 *   <li>{@link #getRGB()} 返回 ARGB 打包值（与 {@code Color#getRGB()} 一致，非预乘）。</li>
 * </ul>
 *
 * @param red   the red component (0-255).
 * @param green the green component (0-255).
 * @param blue  the blue component (0-255).
 * @param alpha the alpha component (0-255).
 */
public record ChunkerColor(int red, int green, int blue, int alpha) {

    /**
     * Create a color from a packed RGB value, alpha is always 255 (matches {@code new Color(int rgb)}).
     *
     * @param rgb the packed color value, red in bits 16-23, green 8-15, blue 0-7.
     */
    public ChunkerColor(int rgb) {
        this((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
    }

    /**
     * Create a color from a packed RGB value, alpha is always 255 (matches {@code new Color(int rgb)}).
     *
     * @param rgb the packed color value, red in bits 16-23, green 8-15, blue 0-7.
     * @return the color.
     */
    public static ChunkerColor fromRGB(int rgb) {
        return new ChunkerColor(rgb);
    }

    /**
     * Create a color from a packed RGBA value (matches {@code new Color(int rgba, boolean hasalpha)}).
     *
     * @param rgba     the packed value: alpha in bits 24-31, red 16-23, green 8-15, blue 0-7.
     * @param hasAlpha whether the alpha component in bits 24-31 should be used (otherwise 255).
     * @return the color.
     */
    public static ChunkerColor fromRGBA(int rgba, boolean hasAlpha) {
        int alpha = hasAlpha ? ((rgba >> 24) & 0xFF) : 255;
        return new ChunkerColor((rgba >> 16) & 0xFF, (rgba >> 8) & 0xFF, rgba & 0xFF, alpha);
    }

    /**
     * Create a color from individual components (matches {@code new Color(int, int, int, int)}).
     *
     * @param red   the red component (0-255).
     * @param green the green component (0-255).
     * @param blue  the blue component (0-255).
     * @param alpha the alpha component (0-255).
     * @return the color.
     */
    public static ChunkerColor fromRGB(int red, int green, int blue, int alpha) {
        return new ChunkerColor(red, green, blue, alpha);
    }

    /**
     * Get the red component.
     *
     * @return the red component (0-255).
     */
    public int getRed() {
        return red;
    }

    /**
     * Get the green component.
     *
     * @return the green component (0-255).
     */
    public int getGreen() {
        return green;
    }

    /**
     * Get the blue component.
     *
     * @return the blue component (0-255).
     */
    public int getBlue() {
        return blue;
    }

    /**
     * Get the alpha component.
     *
     * @return the alpha component (0-255).
     */
    public int getAlpha() {
        return alpha;
    }

    /**
     * Get the packed ARGB value (matches {@code Color#getRGB()}).
     *
     * @return the packed ARGB value.
     */
    public int getRGB() {
        return ((alpha & 0xFF) << 24) | ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
    }
}
