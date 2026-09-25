package com.hivemc.chunker.util;

import com.google.common.io.Resources;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 资源加载桥接（Android 移植专用，不改算法）。
 * <p/>
 * Chunker 核心有两处通过 classpath（Resources.getResource）加载内置资源：
 * - java/CavesAndCliffsHeightPack.zip
 * - java/default_font_width_codepoints.json
 * <p/>
 * Android 的 APK 不把 classpath 资源放到类路径上。运行时由宿主 APP 把这两份资源
 * 从 assets 解压到私有目录后，调用 {@link #setResourceDirectory(File)} 注入目录，
 * 其余代码即可用同一份逻辑读得资源。未注入时回退到 classpath（宿主/CLI 环境正常）。
 */
public final class ChunkerResources {
    private static volatile File resourceDirectory;

    private ChunkerResources() {
    }

    /**
     * 注入额外的资源根目录（Android assets 解压目录）。为 null 时回退到 classpath 加载。
     *
     * @param directory 额外资源根目录，或 null 表示走 classpath。
     */
    public static void setResourceDirectory(File directory) {
        resourceDirectory = directory;
    }

    /**
     * 按资源名打开输入流。优先从注入的目录读取，其次回退到 classpath。
     *
     * @param resourceName 资源名，例如 java/default_font_width_codepoints.json。
     * @return 资源输入流。
     * @throws IOException 若资源不存在且 classpath 也无法加载。
     */
    public static InputStream getResourceAsStream(String resourceName) throws IOException {
        File dir = resourceDirectory;
        if (dir != null) {
            File file = new File(dir, resourceName);
            if (file.isFile()) {
                return new FileInputStream(file);
            }
        }
        return Resources.getResource(resourceName).openStream();
    }

    /**
     * 把资源流复制到目标文件。
     *
     * @param from 输入流（调用方负责关闭）。
     * @param to   目标文件。
     * @throws IOException 复制失败。
     */
    public static void copy(InputStream from, File to) throws IOException {
        try (OutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = from.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }
}