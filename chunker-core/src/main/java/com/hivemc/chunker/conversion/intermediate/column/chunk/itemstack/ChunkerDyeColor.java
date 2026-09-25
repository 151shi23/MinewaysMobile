package com.hivemc.chunker.conversion.intermediate.column.chunk.itemstack;

import com.hivemc.chunker.util.ChunkerColor;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import com.hivemc.chunker.util.ChunkerColor;
import java.util.Map;
import com.hivemc.chunker.util.ChunkerColor;
import java.util.Optional;

/**
 * The color that something can be colored.
 */
public enum ChunkerDyeColor {
    WHITE("white", 0xf0f0f0, 0XF0F0F0),
    ORANGE("orange", 0xF9801D, 0XEB8844),
    MAGENTA("magenta", 0xC74EBD, 0XC354CD),
    LIGHT_BLUE("light_blue", 0x3AB3DA, 0X6689D3),
    YELLOW("yellow", 0xFED83D, 0XDECF2A),
    LIME("lime", 0x80C71F, 0X41CD34),
    PINK("pink", 0xF38BAA, 0XD88198),
    GRAY("gray", 0x474F52, 0X434343),
    LIGHT_GRAY("light_gray", 0x9D9D97, 0XABABAB),
    CYAN("cyan", 0x169C9C, 0X287697),
    PURPLE("purple", 0x8932B8, 0X7B2FBE),
    BLUE("blue", 0x3C44AA, 0X253192),
    BROWN("brown", 0x835432, 0X51301A),
    GREEN("green", 0x5E7C16, 0X3B511A),
    RED("red", 0xB02E26, 0XB3312C),
    BLACK("black", 0x1D1D21, 0X1E1B1B);

    private static final ChunkerDyeColor[] VALUES = values();
    private static final Map<String, ChunkerDyeColor> BY_NAME = new Object2ObjectOpenHashMap<>();

    static {
        for (ChunkerDyeColor value : VALUES) {
            BY_NAME.put(value.getName(), value);
        }
    }

    private final String name;
    private final int bedrockRGB;
    private final int javaRGB;

    /**
     * Create a new dye color.
     *
     * @param name       the identifier used for the color.
     * @param bedrockRGB the bedrock RGB color (used for fireworks).
     * @param javaRGB    the java RGB color (used for fireworks).
     */
    ChunkerDyeColor(String name, int bedrockRGB, int javaRGB) {
        this.name = name;
        this.bedrockRGB = bedrockRGB;
        this.javaRGB = javaRGB;
    }

    /**
     * Get the color by reversed ID (used for bedrock / legacy java).
     *
     * @param id the reversed ID.
     * @return the dye if it was found otherwise empty.
     */
    public static Optional<ChunkerDyeColor> getColorByReversedID(int id) {
        return getColorByID(15 - id); // Invert ID for bedrock
    }

    /**
     * Get the color by ID.
     *
     * @param id the ID.
     * @return the dye if it was found otherwise empty.
     */
    public static Optional<ChunkerDyeColor> getColorByID(int id) {
        if (id < 0 || id >= VALUES.length) return Optional.empty();
        return Optional.of(VALUES[id]);
    }

    /**
     * Get the color by name.
     *
     * @param name the name.
     * @return the dye if it was found otherwise empty.
     */
    public static Optional<ChunkerDyeColor> getColorByName(String name) {
        return Optional.ofNullable(BY_NAME.get(name));
    }

    /**
     * Get the ID of the dye color.
     *
     * @return the ID.
     */
    public int getID() {
        return ordinal();
    }

    /**
     * Get the reversed ID (used for bedrock / legacy java).
     *
     * @return the reversed ID.
     */
    public int getReversedID() {
        return 15 - ordinal(); // Inverted on bedrock/legacy java sometimes
    }

    /**
     * Get the name of the dye as an identifier.
     *
     * @return the name as an identifier.
     */
    public String getName() {
        return name;
    }

    /**
     * Get the Bedrock RGB color used for this dye.
     *
     * @return the bedrock RGB color (used for fireworks).
     */
    public int getBedrockRGB() {
        return bedrockRGB;
    }

    /**
     * Get the Java RGB color used for this dye.
     *
     * @return the java RGB color (used for fireworks).
     */
    public int getJavaRGB() {
        return javaRGB;
    }

    /**
     * Find the closest dye to a given color.
     *
     * @param color the color to find the nearest dye to.
     * @return the closest dye color.
     */
    public static ChunkerDyeColor findClosestDyeColor(ChunkerColor color) {
        double min = Double.MAX_VALUE;
        int minIndex = 0;

        for (int i = 0; i < VALUES.length; i++) {
            ChunkerDyeColor entry = VALUES[i];

            // Try to match Java
            {
                int rgb = entry.getJavaRGB();
                int entryR = (rgb >> 16) & 0xFF;
                int entryG = (rgb >> 8) & 0xFF;
                int entryB = rgb & 0xFF;

                double score = (entryR - color.getRed()) * (entryR - color.getRed()) +
                        (entryG - color.getGreen()) * (entryG - color.getGreen()) +
                        (entryB - color.getBlue()) * (entryB - color.getBlue());

                if (score < min) {
                    min = score;
                    minIndex = i;
                }
            }

            // Try to match Bedrock
            {
                int rgb = entry.getBedrockRGB();
                int entryR = (rgb >> 16) & 0xFF;
                int entryG = (rgb >> 8) & 0xFF;
                int entryB = rgb & 0xFF;

                double score = (entryR - color.getRed()) * (entryR - color.getRed()) +
                        (entryG - color.getGreen()) * (entryG - color.getGreen()) +
                        (entryB - color.getBlue()) * (entryB - color.getBlue());

                if (score < min) {
                    min = score;
                    minIndex = i;
                }
            }
        }

        return VALUES[minIndex];
    }
}
