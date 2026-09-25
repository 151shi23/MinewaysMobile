package com.hivemc.chunker.conversion.encoding.java.base.resolver.itemstack;

import com.hivemc.chunker.util.ChunkerColor;
import com.hivemc.chunker.conversion.encoding.base.Version;
import com.hivemc.chunker.util.ChunkerColor;
import com.hivemc.chunker.resolver.Resolver;

import com.hivemc.chunker.util.ChunkerColor;
import java.util.ArrayList;
import com.hivemc.chunker.util.ChunkerColor;
import java.util.List;
import com.hivemc.chunker.util.ChunkerColor;
import java.util.Optional;

/**
 * Map color array resolver, used for Java
 */
public class JavaMapColorsResolver implements Resolver<byte[], byte[]> {
    public static final int[] MAP_SHADE_MODIFIERS = new int[]{180, 220, 255, 135};
    public final List<ChunkerColor> mapColors;
    public final ChunkerColor[] mapColorsShaded;

    /**
     * Create a new java map color array resolver.
     *
     * @param javaVersion the game version being used, as colors depend on version.
     */
    public JavaMapColorsResolver(Version javaVersion) {
        mapColors = new ArrayList<>();
        mapColors.add(ChunkerColor.fromRGB(0, 0, 0, 0));
        mapColors.add(ChunkerColor.fromRGB(127, 178, 56, 255));
        mapColors.add(ChunkerColor.fromRGB(247, 233, 163, 255));
        mapColors.add(ChunkerColor.fromRGB(199, 199, 199, 255));
        mapColors.add(ChunkerColor.fromRGB(255, 0, 0, 255));
        mapColors.add(ChunkerColor.fromRGB(160, 160, 255, 255));
        mapColors.add(ChunkerColor.fromRGB(167, 167, 167, 255));
        mapColors.add(ChunkerColor.fromRGB(0, 124, 0, 255));
        mapColors.add(ChunkerColor.fromRGB(255, 255, 255, 255));
        mapColors.add(ChunkerColor.fromRGB(164, 168, 184, 255));
        mapColors.add(ChunkerColor.fromRGB(151, 109, 77, 255));
        mapColors.add(ChunkerColor.fromRGB(112, 112, 112, 255));
        mapColors.add(ChunkerColor.fromRGB(64, 64, 255, 255));
        mapColors.add(ChunkerColor.fromRGB(143, 119, 72, 255));
        mapColors.add(ChunkerColor.fromRGB(255, 252, 245, 255));
        mapColors.add(ChunkerColor.fromRGB(216, 127, 51, 255));
        mapColors.add(ChunkerColor.fromRGB(178, 76, 216, 255));
        mapColors.add(ChunkerColor.fromRGB(102, 153, 216, 255));
        mapColors.add(ChunkerColor.fromRGB(229, 229, 51, 255));
        mapColors.add(ChunkerColor.fromRGB(127, 204, 25, 255));
        mapColors.add(ChunkerColor.fromRGB(242, 127, 165, 255));
        mapColors.add(ChunkerColor.fromRGB(76, 76, 76, 255));
        mapColors.add(ChunkerColor.fromRGB(153, 153, 153, 255));
        mapColors.add(ChunkerColor.fromRGB(76, 127, 153, 255));
        mapColors.add(ChunkerColor.fromRGB(127, 63, 178, 255));
        mapColors.add(ChunkerColor.fromRGB(51, 76, 178, 255));
        mapColors.add(ChunkerColor.fromRGB(102, 76, 51, 255));
        mapColors.add(ChunkerColor.fromRGB(102, 127, 51, 255));
        mapColors.add(ChunkerColor.fromRGB(153, 51, 51, 255));
        mapColors.add(ChunkerColor.fromRGB(25, 25, 25, 255));
        mapColors.add(ChunkerColor.fromRGB(250, 238, 77, 255));
        mapColors.add(ChunkerColor.fromRGB(92, 219, 213, 255));
        mapColors.add(ChunkerColor.fromRGB(74, 128, 255, 255));
        mapColors.add(ChunkerColor.fromRGB(0, 217, 58, 255));
        mapColors.add(ChunkerColor.fromRGB(129, 86, 49, 255));
        if (javaVersion.isGreaterThanOrEqual(1, 8, 1)) {
            mapColors.add(ChunkerColor.fromRGB(112, 2, 0, 255));
        }

        // New colors added in 1.12
        if (javaVersion.isGreaterThanOrEqual(1, 12, 0)) {
            mapColors.add(ChunkerColor.fromRGB(209, 177, 161, 255));
            mapColors.add(ChunkerColor.fromRGB(159, 82, 36, 255));
            mapColors.add(ChunkerColor.fromRGB(149, 87, 108, 255));
            mapColors.add(ChunkerColor.fromRGB(112, 108, 138, 255));
            mapColors.add(ChunkerColor.fromRGB(186, 133, 36, 255));
            mapColors.add(ChunkerColor.fromRGB(103, 117, 53, 255));
            mapColors.add(ChunkerColor.fromRGB(160, 77, 78, 255));
            mapColors.add(ChunkerColor.fromRGB(57, 41, 35, 255));
            mapColors.add(ChunkerColor.fromRGB(135, 107, 98, 255));
            mapColors.add(ChunkerColor.fromRGB(87, 92, 92, 255));
            mapColors.add(ChunkerColor.fromRGB(122, 73, 88, 255));
            mapColors.add(ChunkerColor.fromRGB(76, 62, 92, 255));
            mapColors.add(ChunkerColor.fromRGB(76, 50, 35, 255));
            mapColors.add(ChunkerColor.fromRGB(76, 82, 42, 255));
            mapColors.add(ChunkerColor.fromRGB(142, 60, 46, 255));
            mapColors.add(ChunkerColor.fromRGB(37, 22, 16, 255));
        }

        // New colors added in 1.16
        if (javaVersion.isGreaterThan(1, 16, 0)) {
            mapColors.add(ChunkerColor.fromRGB(189, 48, 49, 255));
            mapColors.add(ChunkerColor.fromRGB(148, 63, 97, 255));
            mapColors.add(ChunkerColor.fromRGB(92, 25, 29, 255));
            mapColors.add(ChunkerColor.fromRGB(22, 126, 134, 255));
            mapColors.add(ChunkerColor.fromRGB(58, 142, 140, 255));
            mapColors.add(ChunkerColor.fromRGB(86, 44, 62, 255));
            mapColors.add(ChunkerColor.fromRGB(20, 180, 133, 255));
            mapColors.add(ChunkerColor.fromRGB(100, 100, 100, 255));
            mapColors.add(ChunkerColor.fromRGB(216, 175, 147, 255));
            mapColors.add(ChunkerColor.fromRGB(127, 167, 150, 255));
        }

        // Generate shaded
        mapColorsShaded = new ChunkerColor[mapColors.size() * MAP_SHADE_MODIFIERS.length];
        int index = 0;
        for (ChunkerColor color : mapColors) {
            for (int shade : MAP_SHADE_MODIFIERS) {
                int r = (int) (color.getRed() * shade / 255D);
                int g = (int) (color.getGreen() * shade / 255D);
                int b = (int) (color.getBlue() * shade / 255D);
                int a = color.getAlpha();
                mapColorsShaded[index++] = ChunkerColor.fromRGB(r, g, b, a);
            }
        }
    }

    public byte findClosestJavaColor(int r, int g, int b, int a) {
        int min = Integer.MAX_VALUE;
        int minIndex = 0;

        for (int i = 0; i < mapColorsShaded.length; i++) {
            ChunkerColor entry = mapColorsShaded[i];
            double score = Math.pow(entry.getRed() - r, 2) +
                    Math.pow(entry.getGreen() - g, 2) +
                    Math.pow(entry.getBlue() - b, 2) +
                    Math.pow(entry.getAlpha() - a, 2);
            if (score < min) {
                min = (int) score;
                minIndex = i;
            }
        }

        return (byte) minIndex;
    }

    @Override
    public Optional<byte[]> from(byte[] chunkerMapColors) {
        int index = 0;
        byte[] outputBytes = new byte[chunkerMapColors.length >> 2]; // single byte instead of 4
        for (int i = 0; i < outputBytes.length; i++) {
            int r = chunkerMapColors[index++] & 0xFF;
            int g = chunkerMapColors[index++] & 0xFF;
            int b = chunkerMapColors[index++] & 0xFF;
            int a = chunkerMapColors[index++] & 0xFF;
            outputBytes[i] = findClosestJavaColor(r, g, b, a);
        }

        return Optional.of(outputBytes);
    }

    @Override
    public Optional<byte[]> to(byte[] javaMapColors) {
        byte[] outputBytes = new byte[javaMapColors.length << 2]; // 4 bytes per color instead of 1
        for (int i = 0; i < javaMapColors.length; i++) {
            int value = javaMapColors[i] & 0xFF;

            // Convert to RGB
            if (value < mapColorsShaded.length) {
                ChunkerColor rgba = mapColorsShaded[value];

                int newIndex = i << 2;
                outputBytes[newIndex] = (byte) rgba.getRed();
                outputBytes[newIndex + 1] = (byte) rgba.getGreen();
                outputBytes[newIndex + 2] = (byte) rgba.getBlue();
                outputBytes[newIndex + 3] = (byte) rgba.getAlpha();
            }
        }
        return Optional.of(outputBytes);
    }
}
