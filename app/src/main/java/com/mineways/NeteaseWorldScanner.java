package com.mineways;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 网易版 Minecraft 存档扫描器。
 * <p>
 * 网易版（包名 {@code com.netease.x19}）的存档位于各版本各不相同的私有目录里，
 * 且 Android 11+ 对 {@code /Android/data/<其它包名>} 有强制隔离 —— 普通应用与 SAF 都读不到。
 * 因此本扫描器分两层：
 * <ol>
 *   <li><b>直读</b>：能直接读到就扫（旧安卓、或存档放在公共目录时）；</li>
 *   <li><b>Shizuku 提权</b>：用 shell 的 {@code find/ls} 列出被隔离目录里的世界（应用已内置 Shizuku 支持）。</li>
 * </ol>
 * （第三层「手动指定文件夹」由 {@link NeteaseSaveExporter#exportFromTree} 走 SAF，不在此类内。）
 * <p>
 * 世界判定：目录含 {@code level.dat}，或含带 {@code CURRENT} 的 {@code db} 子目录，或含 {@code region} 子目录。
 * 是否加密用 {@link NeteaseDecryptor#isEncrypted(File)}（新版 XOR）；Shizuku 得来的条目用 shell 读 4 字节头判定。
 */
public final class NeteaseWorldScanner {

    private NeteaseWorldScanner() {
    }

    /** 网易版候选根目录（不同渠道/版本的常见位置；不存在的会自动跳过）。 */
    public static final String[] NETEASE_ROOTS = {
            "/storage/emulated/0/Android/data/com.netease.x19/files/minecraftWorlds",
            "/storage/emulated/0/Android/data/com.netease.x19/files/minecraftpe/minecraftWorlds",
            "/storage/emulated/0/Android/data/com.netease.x19/files/games/com.netease/minecraftWorlds",
            "/storage/emulated/0/Android/data/com.netease.x19/files",
            "/storage/emulated/0/games/com.netease/minecraftWorlds",
            "/storage/emulated/0/games/com.netease",
            "/storage/emulated/0/netease/minecraftWorlds",
            "/storage/emulated/0/minecraftWorlds",
    };

    /** 扫描结果条目。 */
    public static final class World {
        public final String path;
        public final String name;
        public final long sizeBytes;
        public final boolean encrypted;
        public final boolean viaShizuku;
        /** 直读到的为 true；Shizuku 得到的为 false（导出时走 shell 复制）。 */
        public final boolean readable;

        World(String path, String name, long sizeBytes, boolean encrypted,
              boolean viaShizuku, boolean readable) {
            this.path = path;
            this.name = name;
            this.sizeBytes = sizeBytes;
            this.encrypted = encrypted;
            this.viaShizuku = viaShizuku;
            this.readable = readable;
        }

        public String sizeText() {
            if (sizeBytes < 1024) return sizeBytes + " B";
            if (sizeBytes < 1024L * 1024) return String.format(Locale.ROOT, "%.0f KB", sizeBytes / 1024.0);
            if (sizeBytes < 1024L * 1024 * 1024)
                return String.format(Locale.ROOT, "%.1f MB", sizeBytes / 1048576.0);
            return String.format(Locale.ROOT, "%.2f GB", sizeBytes / 1073741824.0);
        }
    }

    public interface Progress {
        void onStep(String message);
    }

    /**
     * 扫描网易版存档。
     *
     * @param allowShizuku 是否允许在直读受限时用 Shizuku 提权扫描。
     * @param cb           进度回调（可为 null；在当前线程回调）。
     */
    public static List<World> scan(boolean allowShizuku, Progress cb) {
        Map<String, World> found = new LinkedHashMap<>();
        List<String> blocked = new ArrayList<>();

        // ---------- 第一层：直读 ----------
        for (String root : NETEASE_ROOTS) {
            if (cb != null) cb.onStep("正在检查：" + root);
            File dir = new File(root);
            if (!dir.isDirectory()) continue;
            File[] kids = dir.listFiles();
            if (kids == null) {
                blocked.add(root);       // 目录存在但被系统隔离
                continue;
            }
            walkForWorlds(dir, found, 0);
        }

        // ---------- 第二层：Shizuku 提权 ----------
        if (allowShizuku) {
            List<String> roots = new ArrayList<>(blocked);
            for (String extra : new String[]{
                    "/storage/emulated/0/Android/data/com.netease.x19",
                    "/storage/emulated/0/Android/data/com.netease.x19/files"}) {
                if (!roots.contains(extra)) roots.add(extra);
            }
            for (String root : roots) {
                if (cb != null) cb.onStep("Shizuku 提权扫描：" + root);
                if (!shizukuExists(root)) continue;
                for (String p : shizukuFindWorlds(root)) {
                    if (found.containsKey(p)) continue;
                    found.put(p, new World(p, baseName(p), shizukuDirSize(p),
                            shizukuIsEncrypted(p), true, false));
                }
            }
        } else if (!blocked.isEmpty() && cb != null) {
            cb.onStep("有 " + blocked.size() + " 个目录被系统隔离（Android 11+ 限制），可改用 Shizuku 提权扫描。");
        }

        List<World> out = new ArrayList<>(found.values());
        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    // ---------------------------------------------------------------- 直读

    /** 递归找世界目录；深度上限 5，避免误扫大目录。 */
    private static void walkForWorlds(File dir, Map<String, World> out, int depth) {
        if (depth > 5) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (!k.isDirectory()) continue;
            if (isWorldDir(k)) {
                String p = k.getAbsolutePath();
                if (!out.containsKey(p)) {
                    out.put(p, new World(p, k.getName(), dirSize(k), isEncryptedSafe(k), false, true));
                }
                continue;   // 世界内部不再继续找
            }
            walkForWorlds(k, out, depth + 1);
        }
    }

    /** 世界判定：level.dat / db+CURRENT / region 任一存在即可（含多套一层同名目录的情况）。 */
    public static boolean isWorldDir(File dir) {
        if (new File(dir, "level.dat").isFile()) return true;
        File db = new File(dir, "db");
        if (db.isDirectory() && new File(db, "CURRENT").isFile()) return true;
        if (new File(dir, "region").isDirectory()) return true;
        File inner = new File(dir, dir.getName());
        return inner.isDirectory()
                && (new File(inner, "level.dat").isFile()
                || new File(new File(inner, "db"), "CURRENT").isFile());
    }

    static boolean isEncryptedSafe(File dir) {
        try {
            return NeteaseDecryptor.isEncrypted(dir);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 目录体积（最多统计 20 万个文件，防止极端情况卡死）。 */
    static long dirSize(File dir) {
        return dirSizeRec(dir, new int[]{0}, 200000);
    }

    private static long dirSizeRec(File dir, int[] count, int cap) {
        File[] kids = dir.listFiles();
        if (kids == null) return 0;
        long total = 0;
        for (File f : kids) {
            if (count[0] > cap) break;
            if (f.isFile()) {
                total += f.length();
                count[0]++;
            } else if (f.isDirectory()) {
                total += dirSizeRec(f, count, cap);
            }
        }
        return total;
    }

    // ---------------------------------------------------------------- Shizuku

    private static boolean shizukuExists(String path) {
        String out = ShizukuWorldImporter.execShell(new String[]{
                "/system/bin/sh", "-c", "[ -d '" + safe(path) + "' ] && echo yes || echo no"});
        return out != null && out.contains("yes");
    }

    /** 用 shell 找世界（level.dat 或 db/CURRENT 所在目录）。 */
    private static List<String> shizukuFindWorlds(String root) {
        List<String> out = new ArrayList<>();
        String r = safe(root);
        String listing = ShizukuWorldImporter.execShell(new String[]{
                "/system/bin/sh", "-c",
                "find '" + r + "' -maxdepth 5 -type f \\( -name level.dat -o -name CURRENT \\) 2>/dev/null"});
        boolean fallback = listing == null || listing.trim().isEmpty();
        if (fallback) {
            // 个别机型的 find 不支持 -maxdepth：退化为两层 ls（此时整行就是目录）
            listing = ShizukuWorldImporter.execShell(new String[]{
                    "/system/bin/sh", "-c",
                    "ls -d '" + r + "/*' '" + r + "/*/*' 2>/dev/null"});
        }
        if (listing == null) return out;
        for (String line : listing.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String parent;
            if (fallback) {
                parent = line;
            } else {
                File f = new File(line);
                if (f.getName().equals("CURRENT")) {
                    File dbDir = f.getParentFile();
                    parent = dbDir == null || dbDir.getParent() == null ? null : dbDir.getParent();
                } else {
                    parent = f.getParent();
                }
            }
            if (parent == null || parent.isEmpty()) continue;
            if (parent.contains("'") || parent.contains("\n")) continue;   // shell 注入防线
            if (!out.contains(parent)) out.add(parent);
        }
        return out;
    }

    /** Shizuku 侧读 4 字节头判断是否网易新版 XOR 加密。 */
    private static boolean shizukuIsEncrypted(String worldPath) {
        String db = null;
        for (String cand : new String[]{worldPath + "/db", worldPath + "/" + baseName(worldPath) + "/db"}) {
            if (shizukuExists(cand)) {
                db = cand;
                break;
            }
        }
        if (db == null) return false;
        String head = ShizukuWorldImporter.execShell(new String[]{
                "/system/bin/sh", "-c", "od -An -tx1 -N4 '" + safe(db) + "/CURRENT' 2>/dev/null"});
        if (head == null) return false;
        String hex = head.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        return hex.startsWith("801d3001");   // NeteaseDecryptor.ENCRYPT_HEADER
    }

    /** Shizuku 侧算目录体积（du -sk，单位 KB）。 */
    private static long shizukuDirSize(String path) {
        String out = ShizukuWorldImporter.execShell(new String[]{
                "/system/bin/sh", "-c", "du -sk '" + safe(path) + "' 2>/dev/null"});
        if (out == null) return 0;
        for (String line : out.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+");
            try {
                return Long.parseLong(parts[0]) * 1024L;
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    /** 只允许在 shell 参数里出现安全的路径（拒绝引号/换行，防注入）。 */
    private static String safe(String path) {
        if (path == null) return "";
        return path.replace("'", "").replace("\n", "").replace("\r", "");
    }

    private static String baseName(String path) {
        if (path == null) return "";
        int i = path.lastIndexOf('/');
        return i >= 0 ? path.substring(i + 1) : path;
    }
}
