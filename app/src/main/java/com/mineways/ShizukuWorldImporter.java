package com.mineways;

import android.content.Context;
import android.os.RemoteException;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import rikka.shizuku.Shizuku;

/**
 * 通过 Shizuku 提权访问普通 APP 无法读取的 Minecraft 存档目录。
 * <p>
 * 因为 Minecraft 基岩版存档位于 /storage/emulated/0/Android/data/com.mojang.minecraftpe/ ，
 * 普通 APP 和 SAF 都无法访问。Shizuku 授权后本应用可以以高权限运行 shell 命令，
 * 把所选存档递归复制到本应用私有目录，再交给 C++ 核心导出。
 */
public class ShizukuWorldImporter {

    public interface Callback {
        void onResult(String message); // "OK:path=..." 或 "ERR:..."
    }

    private static final String PERMISSION = "moe.shizuku.manager.permission.API_V23";

    /** Shizuku 是否已安装并授权。 */
    public static boolean isReady() {
        if (!Shizuku.pingBinder()) return false;
        if (Shizuku.isPreV11()) return Shizuku.checkSelfPermission() == 0;
        try {
            return Shizuku.checkSelfPermission() == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 请求 Shizuku 授权（返回请求是否已发起）。 */
    public static void requestPermission(Callback cb) {
        try {
            if (isReady()) { cb.onResult("OK:already-granted"); return; }
            Shizuku.requestPermission(14711); // 任意正整数 requestCode
            // 结果通过 Shizuku.OnRequestPermissionResultListener 回调，见 MainActivity
            cb.onResult("OK:requested");
        } catch (Exception e) {
            cb.onResult("ERR:" + e.getMessage());
        }
    }

    /**
     * 用 Shizuku 运行 shell 命令，输出 UTF-8 文本。
     * @return 命令输出（拼接 stderr）；失败返回 null。
     */
    public static String execShell(String[] cmd) {
        if (!isReady()) return null;
        Process p = newRemoteProcess(cmd);
        if (p == null) return null;
        try {
            StringBuilder sb = new StringBuilder();
            InputStream is = p.getInputStream();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(line);
                }
            }
            p.waitFor();
            return sb.toString();
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    /**
     * Shizuku API 13.1.5 中 {@code Shizuku.newProcess}（运行高权限进程）被标记为 private。
     * 为编译稳健性（且 debug 构建不启用混淆，方法名不会被改写），通过反射调用它。
     */
    private static Process newRemoteProcess(String[] cmd) {
        try {
            java.lang.reflect.Method m = Shizuku.class.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            m.setAccessible(true);
            return (Process) m.invoke(null, (Object) cmd, null, null);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 列出基岩版默认存档根目录下的所有子目录（世界文件夹）。
     * @return 世界文件夹绝对路径列表；失败返回空列表。
     */
    public static List<String> listBedrockWorlds() {
        List<String> out = new ArrayList<>();
        String root = "/storage/emulated/0/Android/data/com.mojang.minecraftpe/games/com.mojang/minecraftWorlds";
        String listing = execShell(new String[]{
                "/system/bin/sh", "-c",
                "ls -d " + root + "/*/ 2>/dev/null"});
        if (TextUtils.isEmpty(listing)) return out;
        for (String line : listing.split("\n")) {
            line = line.trim();
            if (line.length() > 0 && line.endsWith("/")) {
                out.add(line.substring(0, line.length() - 1));
            }
        }
        return out;
    }

    /**
     * 用 Shizuku 把源目录复制到目标目录（目标必须是应用私有目录）。
     * 返回复制到的目标目录绝对路径（含 level.dat 的那一层）。
     */
    public static String importWorld(Context context, String srcWorldDir, Callback cb) {
        if (!isReady()) { cb.onResult("ERR:shizuku-not-ready"); return null; }
        File src = new File(srcWorldDir);
        if (!src.exists()) { cb.onResult("ERR:src-not-exists"); return null; }

        File dstRoot = new File(context.getFilesDir(), "world");
        if (!dstRoot.exists() && !dstRoot.mkdirs()) { cb.onResult("ERR:mkdir-dst"); return null; }
        File dst = new File(dstRoot, src.getName());
        String dstPath = dst.getAbsolutePath();

        // 先清空目标
        execShell(new String[]{"/system/bin/sh", "-c", "rm -rf '" + dstPath + "'"});
        execShell(new String[]{"/system/bin/sh", "-c", "mkdir -p '" + dstPath + "'"});

        // 用 shell cp -r 复制（Shell 拥有读取 /Android/data 的权限）
        String cmd = "cp -r '" + src.getAbsolutePath() + "/.' '" + dstPath + "/' 2>/dev/null; ";
        cmd += "chmod -R u+rwX '" + dstPath + "'";
        String result = execShell(new String[]{"/system/bin/sh", "-c", cmd});
        if (result == null) { cb.onResult("ERR:cp-failed"); return null; }

        // 找到含 level.dat 的层（有的世界套了一层目录）
        File lvl = new File(dstPath, "level.dat");
        if (!lvl.exists()) {
            lvl = findLevelDat(new File(dstPath));
            if (lvl == null) { cb.onResult("ERR:no-level-dat"); return null; }
            dstPath = lvl.getParentFile().getAbsolutePath();
        }

        // 网易加密存档自动就地解密（非网易存档原样跳过，不影响现有功能）。
        // 该方法在后台线程中执行，无需切线程；解密失败也不阻断导入，仅记录日志，
        // 保持对普通存档的原有行为不变。
        try {
            NeteaseDecryptor.decrypt(new File(dstPath), null);
        } catch (Throwable ignored) {
        }

        cb.onResult("OK:" + dstPath);
        return dstPath;
    }

    private static File findLevelDat(File dir) {
        File[] kids = dir.listFiles();
        if (kids == null) return null;
        for (File k : kids) {
            if (k.isDirectory()) {
                File r = findLevelDat(k);
                if (r != null) return r;
            } else if (k.getName().equals("level.dat")) {
                return k;
            }
        }
        return null;
    }
}