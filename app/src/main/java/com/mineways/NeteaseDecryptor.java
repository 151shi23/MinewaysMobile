package com.mineways;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 网易版 Minecraft 存档 XOR 解密器。
 * <p/>
 * 移植自 {@code ihaiming/NetEaseMC-Decryptor}（GPL v3.0）的纯前端核心算法，
 * 并与社区其它实现（Jerbvsjhs/NeteaseMcDencrypter、HTMonkeyG/XOR-MC-Archive-Decrypt）交叉核对一致。
 * <p/>
 * 网易加密存档的本质是：存档目录下的 {@code db} 子目录内，每个文件都被改写为
 * {@code 4 字节加密头 + 使用 8/16 字节密钥按字节循环异或( XOR )的密文}。
 * <ul>
 *   <li>加密头：{@code 0x80 0x1D 0x30 0x01}（新版，可本地解密）；</li>
 *   <li>旧版加密头：{@code 0x90 0x1D 0x30 0x01}（AES-CFB8，无法本地解密）；</li>
 *   <li>密钥：取 {@code db/CURRENT}（去掉 4 字节头）与 {@code MANIFEST 文件名字节 + 0x0A} 循环异或后得到。</li>
 * </ul>
 * 本类为纯 Java，不依赖 Android API，可在任意 JVM 上单独编译与测试。
 * 解密时只就地改写“带加密头的文件”，对未加密文件不做任何改动，因此不影响现有功能。
 */
public final class NeteaseDecryptor {

    /** 新版 XOR 加密头。 */
    static final byte[] ENCRYPT_HEADER = {(byte) 0x80, 0x1D, 0x30, 0x01};

    /** 旧版加密头（AES-CFB8），本工具无法解密，仅用于识别提示。 */
    static final byte[] LEGACY_HEADER = {(byte) 0x90, 0x1D, 0x30, 0x01};

    /** CURRENT 文件名。 */
    private static final String CURRENT_NAME = "CURRENT";

    /** MANIFEST 文件名前缀，实际名称可能为 MANIFEST 或 MANIFEST-0000000000000001。 */
    private static final String MANIFEST_PREFIX = "MANIFEST";

    private NeteaseDecryptor() {
    }

    /** 解密结果。 */
    public static final class Result {
        /** 是否识别为“网易 XOR 加密存档”。 */
        public final boolean encrypted;
        /** 是否成功完成解密。 */
        public final boolean success;
        /** 已解密文件数。 */
        public final int decryptedFiles;
        /** 定位到的 db 目录；未加密/未找到时为 null。 */
        public final File dbDir;
        /** 人类可读信息。 */
        public final String message;

        Result(boolean encrypted, boolean success, int decryptedFiles,
               File dbDir, String message) {
            this.encrypted = encrypted;
            this.success = success;
            this.decryptedFiles = decryptedFiles;
            this.dbDir = dbDir;
            this.message = message;
        }
    }

    /** 进度回调接口（后台线程调用）。 */
    public interface ProgressListener {
        /** 当前处理的文件名与进度比例 0~1。 */
        void onProgress(String file, double ratio);
    }

    /**
     * 判断存档是否属于网易 XOR 加密格式。
     *
     * @param worldRoot 存档根目录（应包含 level.dat 与 db 子目录）。
     * @return true 表示存在带新版加密头的 db 目录。
     */
    public static boolean isEncrypted(File worldRoot) {
        try {
            File db = locateDbDir(worldRoot);
            if (db == null) return false;
            File current = new File(db, CURRENT_NAME);
            byte[] head = readHead(current, ENCRYPT_HEADER.length);
            return head != null && matches(head, ENCRYPT_HEADER);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 对指定存档根目录执行就地解密。
     * <p/>
     * 若检测到网易 XOR 加密则解密其中所有带加密头的文件；否则原样返回，
     * 不对现有存档做任何改动。
     *
     * @param worldRoot 存档根目录（应包含 level.dat 与 db 子目录）。
     * @param listener  可选的进度回调，可能为 null。
     * @return 解密结果。
     */
    public static Result decrypt(File worldRoot, ProgressListener listener) {
        File db;
        try {
            db = locateDbDir(worldRoot);
        } catch (IOException e) {
            return new Result(false, false, 0, null, "扫描存档目录失败：" + e.getMessage());
        }
        if (db == null) {
            return new Result(false, false, 0, null, "未找到存档 db 目录（不是网易加密存档）。");
        }

        // 1) 读取 CURRENT 头部，判断加密格式
        File current = new File(db, CURRENT_NAME);
        byte[] currentBytes;
        try {
            currentBytes = readAll(current);
        } catch (IOException e) {
            return new Result(false, false, 0, db, "读取 CURRENT 失败：" + e.getMessage());
        }
        if (currentBytes == null || currentBytes.length < ENCRYPT_HEADER.length) {
            // 没有 CURRENT 或内容过短：视为未加密（沿用原逻辑，不中断）
            return new Result(false, false, 0, db, "存档未加密或缺少 CURRENT 文件。");
        }
        if (matches(currentBytes, ENCRYPT_HEADER)) {
            // 新版 XOR 加密，可本地解密
        } else if (matches(currentBytes, LEGACY_HEADER)) {
            return new Result(true, false, 0, db, "检测到旧版(AES)加密存档，无法本地解密。");
        } else {
            return new Result(false, false, 0, db, "存档未采用网易 XOR 加密。");
        }

        // 2) 定位 MANIFEST 文件名，构造密钥
        String manifestName = findManifestName(db);
        if (manifestName == null) {
            return new Result(true, false, 0, db, "未找到 MANIFEST 文件，无法生成解密密钥。");
        }
        byte[] key;
        try {
            key = deriveKey(currentBytes, manifestName);
        } catch (Exception e) {
            return new Result(true, false, 0, db, "密钥推导失败：" + e.getMessage());
        }
        if (key == null || key.length == 0) {
            return new Result(true, false, 0, db, "密钥推导失败：CURRENT 内容为空。");
        }

        // 3) 遍历 db 目录，就地解密带加密头的文件
        File[] files = db.listFiles();
        if (files == null) {
            return new Result(true, false, 0, db, "无法读取 db 目录。");
        }
        List<File> list = new ArrayList<>();
        for (File f : files) {
            if (f.isFile()) list.add(f);
        }
        int total = list.size();
        int decrypted = 0;
        for (int i = 0; i < total; i++) {
            File f = list.get(i);
            String name = f.getName();
            if (listener != null) listener.onProgress(name, total == 0 ? 1.0 : (double) i / total);
            try {
                if (decryptFileInPlace(f, key)) decrypted++;
            } catch (IOException e) {
                // 单个文件失败不影响整体（与原版行为一致）
                // 仅跳过错杀未加密文件的情况；此处保留原文件
            }
        }
        if (listener != null) listener.onProgress("", 1.0);

        return new Result(true, true, decrypted, db,
                String.format(Locale.ROOT, "解密完成：共处理 %d 个文件。", decrypted));
    }

    // ---------------------------------------------------------------- 核心算法

    /**
     * 由 CURRENT 文件内容与 MANIFEST 文件名推导解密密钥。
     *
     * @param currentBytes CURRENT 完整文件内容（含 4 字节头）。
     * @param manifestName MANIFEST 文件名（不含路径）。
     * @return 8 或 16 字节密钥；推导失败返回 null。
     */
    static byte[] deriveKey(byte[] currentBytes, String manifestName) {
        // 1) CURRENT 去掉 4 字节加密头
        byte[] encryptedData = Arrays.copyOfRange(currentBytes, ENCRYPT_HEADER.length, currentBytes.length);

        // 2) 源数据 = MANIFEST 文件名(UTF-8) + 0x0A
        byte[] manifestBytes = manifestName.getBytes(StandardCharsets.UTF_8);
        byte[] source = new byte[manifestBytes.length + 1];
        System.arraycopy(manifestBytes, 0, source, 0, manifestBytes.length);
        source[manifestBytes.length] = 0x0A;

        // 与原版一致：无论长度是否相等都执行循环异或（xor 以 key 长度取模循环）。
        // 正常存档中 CURRENT 去掉头后的长度 == MANIFEST文件名长度+1。
        byte[] key = xor(encryptedData, source);
        return optimizeKey(key);
    }

    /** 对当前文件做就地解密；若带加密头则改写，否则保持不变。返回是否发生了改写。 */
    private static boolean decryptFileInPlace(File file, byte[] key) throws IOException {
        byte[] head = readHead(file, ENCRYPT_HEADER.length);
        if (head == null || !matches(head, ENCRYPT_HEADER)) {
            return false; // 未加密文件，不改动
        }
        byte[] all = readAll(file);
        byte[] body = Arrays.copyOfRange(all, ENCRYPT_HEADER.length, all.length);
        byte[] plain = xor(body, key);
        writeAll(file, plain);
        return true;
    }

    /** 在 db 目录内定位 MANIFEST（支持 MANIFEST 或 MANIFEST-xxx）。 */
    private static String findManifestName(File db) {
        File[] files = db.listFiles();
        if (files == null) return null;
        String fallback = null;
        for (File f : files) {
            if (!f.isFile()) continue;
            String name = f.getName();
            if (name.equals(MANIFEST_PREFIX)) return name;
            if (name.startsWith(MANIFEST_PREFIX + "-")) fallback = name;
        }
        return fallback;
    }

    /**
     * 定位存档的 db 目录。网易加密文件都位于 db 目录下，因此
     * 递归在 worldRoot 下寻找同时包含 CURRENT 与 MANIFEST 的目录。
     */
    static File locateDbDir(File worldRoot) throws IOException {
        if (worldRoot == null || !worldRoot.isDirectory()) return null;
        return findDbDir(worldRoot);
    }

    private static File findDbDir(File dir) {
        if (dir.isDirectory()) {
            File current = new File(dir, CURRENT_NAME);
            boolean hasManifest = hasManifest(dir);
            if (current.isFile() && hasManifest) {
                return dir;
            }
            // 目录名即是 db 时优先命中
            if ("db".equalsIgnoreCase(dir.getName())) {
                return dir;
            }
            File[] kids = dir.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    if (k.isDirectory()) {
                        File r = findDbDir(k);
                        if (r != null) return r;
                    }
                }
            }
        }
        return null;
    }

    private static boolean hasManifest(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (!f.isFile()) continue;
            String name = f.getName();
            if (name.equals(MANIFEST_PREFIX) || name.startsWith(MANIFEST_PREFIX + "-")) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 原语

    /** 循环异或：result[i] = data[i] ^ key[i % key.length]。 */
    private static byte[] xor(byte[] data, byte[] key) {
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) (data[i] ^ key[i % key.length]);
        }
        return out;
    }

    /**
     * 优化密钥：若为 16 字节且前后 8 字节相同，则只取前 8 字节。
     * （不同实现的本分支均为 8 字节密钥，此优化用于兼容。）
     */
    private static byte[] optimizeKey(byte[] key) {
        if (key != null && key.length == 16) {
            boolean same = true;
            for (int i = 0; i < 8; i++) {
                if (key[i] != key[i + 8]) {
                    same = false;
                    break;
                }
            }
            if (same) return Arrays.copyOfRange(key, 0, 8);
        }
        return key;
    }

    private static boolean matches(byte[] data, byte[] pattern) {
        if (data == null || data.length < pattern.length) return false;
        for (int i = 0; i < pattern.length; i++) {
            if (data[i] != pattern[i]) return false;
        }
        return true;
    }

    /** 读取文件前 N 字节；文件不足 N 字节或不读时返回 null。 */
    private static byte[] readHead(File file, int n) throws IOException {
        if (!file.isFile()) return null;
        byte[] buf = new byte[n];
        try (InputStream in = new FileInputStream(file)) {
            int off = 0;
            while (off < n) {
                int r = in.read(buf, off, n - off);
                if (r < 0) return null; // 文件不足 n 字节
                off += r;
            }
        }
        return buf;
    }

    private static byte[] readAll(File file) throws IOException {
        if (!file.isFile()) return null;
        try (InputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static void writeAll(File file, byte[] data) throws IOException {
        File tmp = new File(file.getAbsolutePath() + ".neteasemc-tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
            out.flush();
        }
        if (!tmp.renameTo(file)) {
            // 跨文件系统或特殊情况下 rename 失败，则用复制兜底
            try (InputStream in = new FileInputStream(tmp);
                 OutputStream out = new FileOutputStream(file)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
            //noinspection ResultOfMethodCallBeingIgnored
            tmp.delete();
        }
    }
}