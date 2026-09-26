package com.mineways.mod;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 模组支持·输出：把解析出的模组网格**以追加方式**合并进已导出的 OBJ/MTL。
 * 坐标映射不用猜：用「扫描到的原版方块世界包围盒」与「OBJ 实测顶点包围盒」解出逐轴平移量
 * （核心默认与世界坐标同向，没有镜像，见 solveMap 注释）。
 * 全程追加式（先写临时文件再拼接），失败时原版导出结果不受影响。
 */
public final class ModOutput {

    public static final class Result {
        public boolean merged;
        public String report = "";
    }

    private ModOutput() { }

    public static Result run(Context ctx, File objFile, File mtlFile, File texDir,
                             File worldDir, int dim, boolean newFormat,
                             int minx, int miny, int minz, int maxx, int maxy, int maxz,
                             List<Uri> uris) {
        Result r = new Result();
        StringBuilder rep = new StringBuilder("[模组转换] ");
        try {
            ModBlockScanner.Result scan = ModBlockScanner.scan(worldDir, dim, newFormat,
                    minx, miny, minz, maxx, maxy, maxz);
            if (scan.instances.isEmpty()) {
                r.report += "选区内没有模组方块（原版方块由核心处理）。\n";
                r.report += join(scan.notes);
                return r;
            }
            rep.append("选区内模组方块 ").append(scan.counts.size()).append(" 种 / ")
               .append(scan.instances.size()).append(" 个实例\n");

            ModModelResolver.Idx idx = ModModelResolver.index(ctx, uris);
            if (idx.models.isEmpty() && idx.states.isEmpty()) {
                r.report += "未能从所选文件解析出任何模型/方块状态（需要的是模组 jar 或资源包）—— 已跳过合并。\n";
                r.report += join(scan.notes);
                return r;
            }

            double[] objB = objBBox(objFile);
            double[] map;
            if (objB != null && scan.vanillaCount > 0) {
                map = solveMap(objB, scan);
                // 护栏：核心若做了整体缩放或换轴旋转（3D 打印尺寸 / 旋转 / Z-up），
                // 只靠平移对齐就会把模组段放歪。这里用「OBJ 实测跨度」与「扫描器量到的
                // 原版方块跨度」互相印证 —— 两者出自同一批方块，正常必须相等。
                double dx = Math.abs((objB[3] - objB[0]) - (scan.vmaxX - scan.vminX + 1));
                double dy = Math.abs((objB[4] - objB[1]) - (scan.vmaxY - scan.vminY + 1));
                double dz = Math.abs((objB[5] - objB[2]) - (scan.vmaxZ - scan.vminZ + 1));
                if (Math.max(dx, Math.max(dy, dz)) > 2.5) {
                    r.report += "已跳过合并：核心对模型做了整体缩放或换轴旋转（3D 打印尺寸 / 旋转 / Z-up 等），"
                              + "模组段无法只靠平移对齐，硬合进去会把方块放歪。\n";
                    r.report += "    跨度对比（OBJ 实测 vs 原版方块）：" + f3(objB[3] - objB[0]) + " / "
                              + f3(objB[4] - objB[1]) + " / " + f3(objB[5] - objB[2]) + " 比 "
                              + f3(scan.vmaxX - scan.vminX + 1) + " / " + f3(scan.vmaxY - scan.vminY + 1)
                              + " / " + f3(scan.vmaxZ - scan.vminZ + 1) + "\n";
                    r.report += join(scan.notes);
                    return r;
                }
                rep.append("坐标标定：与世界坐标同向（核心不做 X 镜像），偏移=(")
                   .append(f3(map[0])).append(", ").append(f3(map[1]))
                   .append(", ").append(f3(map[2])).append(")\n");
            } else {
                // 选区内没有原版方块可标定（纯模组地图就是这种情况）：核心的默认约定就是
                // obj = 世界坐标（1 单位 = 1 方块、不镜像），模组段用同一套坐标落地，不叠加任何偏移。
                map = new double[]{0, 0, 0};
                rep.append("坐标标定：选区内没有原版方块可比对，按核心默认约定输出（与世界坐标同向、零偏移）\n");
            }

            if (!texDir.isDirectory()) texDir.mkdirs();
            File objTmp = new File(objFile.getAbsolutePath() + ".modtmp");
            File mtlTmp = new File(mtlFile.getAbsolutePath() + ".modtmp");
            long vBase = countPrefix(objFile, "v ");
            long tBase = countPrefix(objFile, "vt ");
            StringBuilder objAdd = new StringBuilder(), mtlAdd = new StringBuilder();
            Map<String, String> matDone = new HashMap<>();
            Map<String, String> texFile = new HashMap<>();
            int ok = 0, degraded = 0, mi = 0;

            for (Map.Entry<String, Integer> e : scan.counts.entrySet()) {
                String state = e.getKey();
                ModModelResolver.Resolved rd = ModModelResolver.resolve(state, idx);
                if (rd.quads.isEmpty()) { degraded++; continue; }
                ok++;
                // 一个方块的不同面可能用不同贴图（柱、工作台一类），所以材质按「贴图」建，
                // 逐面切换 usemtl；只按方块建材质会让所有面都贴上第一张图。
                objAdd.append("\no mod_block_").append(++mi).append('_').append(sanitize(state)).append('\n');
                for (ModBlockScanner.Inst inst : scan.instances) {
                    if (!inst.state.equals(state)) continue;
                    String cur = null;
                    for (ModModelResolver.Quad q : rd.quads) {
                        String mat = material(ctx, idx, q.tex, texDir, texFile, matDone, mtlAdd);
                        if (!mat.equals(cur)) { objAdd.append("usemtl ").append(mat).append('\n'); cur = mat; }
                        objAdd.append(emitQuad(q, inst.x, inst.y, inst.z, map, vBase, tBase));
                        vBase += 4;
                        tBase += 4;
                    }
                }
            }
            rep.append("解析成功 ").append(ok).append(" 种，降级 ").append(degraded)
               .append(" 种（缺模型/multipart/连接性方块 → 占位色立方体）\n");

            if (ok > 0) {
                boolean objCreated = ensureMtlLib(objFile, mtlFile);
                append(objFile, objTmp, objAdd.toString());
                append(mtlFile, mtlTmp, mtlAdd.toString());
                r.merged = true;
                if (objCreated)
                    rep.append("核心没在选区内找到原版方块（没有产出 OBJ），已新建仅含模组方块的 OBJ\n");
                rep.append("已合并进 OBJ/MTL（模组段独立成组，材质名前缀 mod_）\n");
            }
            r.report = rep.toString() + join(scan.notes);
            return r;
        } catch (Throwable t) {
            r.report = "[模组转换] 失败：" + t + "（原版导出结果未受影响）\n";
            return r;
        }
    }

    private static String join(List<String> notes) {
        if (notes == null || notes.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String n : notes) sb.append("    （").append(n).append("）\n");
        return sb.toString();
    }

    /**
     * 解出逐轴平移量 {offX, offY, offZ}：objX = 世界X + offX（Y/Z 同理）。
     *
     * 只解平移，不判镜像。核心在默认配置下顶点是原样输出的：
     *   ObjFileManip.cpp 里顶点 = (anchor[X] - gModel.center[X]) * scale * unitsScale，
     *   未居中时 gModel.center = gWorld2BoxOffset，而 gWorld2BoxOffset[X] = 1 - 选区minX
     *   （Y/Z 同号，见 initializeWorldData），anchor[X] 又是「世界X + gWorld2BoxOffset[X]」，
     *   两者相减正好等于世界 X —— 即 objX 就是世界 X，没有任何左右翻转。
     * 「X 轴镜像」出自 export_diag 的方向自检文案，而那条自检在同向与镜像两组判据上
     * 用的是同一对比较（选区对称于 x=-0.5 时两组同时成立），并且先判镜像，于是报出镜像 ——
     * 那是误报。旧代码照它写死镜像，选区 X 跨度靠近原点时会把整片模组方块挪出去半个跨度。
     *
     * 偏移量用「OBJ 实测最小顶点 − 扫描到的原版方块最小坐标」现场量，不写死常量，
     * 这样核心做纯平移类的改动（如「居中模型」）也能自动跟上；
     * 缩放 / 换轴旋转则由调用方的跨度护栏拦下。
     */
    private static double[] solveMap(double[] objB, ModBlockScanner.Result scan) {
        return new double[]{objB[0] - scan.vminX, objB[1] - scan.vminY, objB[2] - scan.vminZ};
    }

    private static double[] objBBox(File objFile) {
        double[] b = null;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(objFile), StandardCharsets.UTF_8))) {
            String ln;
            while ((ln = r.readLine()) != null) {
                if (ln.length() < 2 || ln.charAt(0) != 'v' || ln.charAt(1) != ' ') continue;
                String[] p = ln.trim().split("\\s+");
                if (p.length < 4) continue;
                double x = Double.parseDouble(p[1]), y = Double.parseDouble(p[2]), z = Double.parseDouble(p[3]);
                if (b == null) b = new double[]{x, y, z, x, y, z};
                else {
                    b[0] = Math.min(b[0], x); b[1] = Math.min(b[1], y); b[2] = Math.min(b[2], z);
                    b[3] = Math.max(b[3], x); b[4] = Math.max(b[4], y); b[5] = Math.max(b[5], z);
                }
            }
        } catch (Throwable ignored) { }
        return b;
    }

    private static long countPrefix(File f, String prefix) {
        long n = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String ln;
            while ((ln = r.readLine()) != null) if (ln.startsWith(prefix)) n++;
        } catch (Throwable ignored) { }
        return n;
    }

    private static String emitQuad(ModModelResolver.Quad q, int wx, int wy, int wz,
                                   double[] map, long vBase, long tBase) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i += 3) {
            sb.append("v ").append(f3(q.p[i] + wx + map[0]))
              .append(' ').append(f3(q.p[i + 1] + wy + map[1]))
              .append(' ').append(f3(q.p[i + 2] + wz + map[2])).append('\n');
        }
        for (int i = 0; i < 8; i += 2) sb.append("vt ").append(f3(q.uv[i])).append(' ').append(f3(q.uv[i + 1])).append('\n');
        sb.append("f ");
        for (int i = 0; i < 4; i++) sb.append(vBase + i + 1).append('/').append(tBase + i + 1).append(i < 3 ? ' ' : '\n');
        return sb.toString();
    }

    private static String f3(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.valueOf(Math.round(v * 1000000.0) / 1000000.0);
    }

    private static String f2(float v) {
        return String.valueOf(Math.round(v * 1000f) / 1000f);
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^0-9A-Za-z]", "_");
    }

    /**
     * 保证 OBJ 里有 mtllib：否则查看器不会去读 .mtl，模组方块全都没贴图（只剩白模）。
     * 纯模组选区时核心找不到原版方块、一个文件都不产出，OBJ 根本不存在——这里连文件头一起建出来。
     * 返回 true 表示 OBJ 是本次新建的（即核心没有产出 OBJ）。
     */
    private static boolean ensureMtlLib(File objFile, File mtlFile) throws Exception {
        boolean existed = objFile.isFile();
        if (existed) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(objFile), StandardCharsets.UTF_8))) {
                String ln;
                int n = 0;
                while ((ln = r.readLine()) != null && n++ < 600) if (ln.startsWith("mtllib ")) return false;
            }
        }
        File tmp = new File(objFile.getAbsolutePath() + ".modhdr");
        try (OutputStream os = new FileOutputStream(tmp)) {
            os.write(("# Wavefront OBJ file - 模组方块由 MinewaysMobile 追加\n"
                    + "mtllib " + mtlFile.getName() + "\n").getBytes(StandardCharsets.UTF_8));
            if (existed) {
                try (InputStream is = new FileInputStream(objFile)) {
                    byte[] b = new byte[8192];
                    int n;
                    while ((n = is.read(b)) > 0) os.write(b, 0, n);
                }
            }
        }
        try (OutputStream os = new FileOutputStream(objFile, false);
             InputStream is = new FileInputStream(tmp)) {
            byte[] b = new byte[8192];
            int n;
            while ((n = is.read(b)) > 0) os.write(b, 0, n);
        }
        tmp.delete();
        return !existed;
    }

    private static void append(File target, File tmp, String addition) throws Exception {
        try (OutputStream os = new FileOutputStream(tmp)) {
            os.write(addition.getBytes(StandardCharsets.UTF_8));
        }
        try (OutputStream os = new FileOutputStream(target, true);
             InputStream is = new FileInputStream(tmp)) {
            byte[] b = new byte[8192];
            int n;
            while ((n = is.read(b)) > 0) os.write(b, 0, n);
        }
        tmp.delete();
    }

    /** 一种贴图一个材质：同名贴图全模型共用，只有第一次出现时才写进 MTL。 */
    private static String material(Context ctx, ModModelResolver.Idx idx, String texPath, File texDir,
                                   Map<String, String> texFile, Map<String, String> matDone, StringBuilder mtlAdd) {
        CRC32 crc = new CRC32();
        crc.update((texPath == null ? "missing" : texPath).getBytes(StandardCharsets.UTF_8));
        String mat = "mod_" + Long.toHexString(crc.getValue());
        if (matDone.containsKey(mat)) return mat;
        String tf = texPath == null ? null : texFile.get(texPath);
        if (tf == null && texPath != null) {
            tf = writeTexture(ctx, idx, texPath, texDir);
            texFile.put(texPath, tf);
        }
        String mapLine;
        if (tf != null) mapLine = "map_Kd " + texDir.getName() + "/" + tf + "\n";
        else {
            float[] c = avgColor(ctx, idx, texPath);
            mapLine = "Kd " + f2(c[0]) + " " + f2(c[1]) + " " + f2(c[2]) + "\n";
        }
        matDone.put(mat, mapLine);
        mtlAdd.append("\nnewmtl ").append(mat).append("\nKd 1 1 1\nKs 0 0 0\n").append(mapLine);
        return mat;
    }

    // ================= 贴图落地 =================

    private static String writeTexture(Context ctx, ModModelResolver.Idx idx, String ref, File texDir) {
        try {
            String ref2 = ref.endsWith(".png") ? ref : ref + ".png";
            String entry = idx.texEntry.containsKey(ref2) ? idx.texEntry.get(ref2)
                    : idx.texEntry.get(stripNs(ref2));
            if (entry == null) {
                String rel = ref2.contains(":") ? ref2.substring(ref2.indexOf(':') + 1) : ref2;
                for (String k : idx.texEntry.keySet()) {
                    if (k.endsWith(rel)) { entry = idx.texEntry.get(k); break; }
                }
            }
            if (entry == null) return null;
            byte[] png = readEntry(ctx, idx, entry);
            if (png == null) return null;
            Bitmap bm = BitmapFactory.decodeByteArray(png, 0, png.length);
            CRC32 crc = new CRC32();
            crc.update(entry.getBytes(StandardCharsets.UTF_8));
            String fn = "mod_" + Long.toHexString(crc.getValue()) + ".png";
            File out = new File(texDir, fn);
            if (!out.isFile() && bm != null) {
                try (OutputStream os = new FileOutputStream(out)) { bm.compress(Bitmap.CompressFormat.PNG, 100, os); }
            }
            if (bm != null) bm.recycle();
            return out.isFile() ? fn : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String stripNs(String s) {
        return s != null && s.contains(":") ? s.substring(s.indexOf(':') + 1) : s;
    }

    private static float[] avgColor(Context ctx, ModModelResolver.Idx idx, String ref) {
        float[] c = {0.55f, 0.55f, 0.55f};
        try {
            String ref2 = ref == null ? "" : (ref.endsWith(".png") ? ref : ref + ".png");
            String entry = idx.texEntry.get(ref2);
            if (entry == null) entry = idx.texEntry.get(stripNs(ref2));
            byte[] png = entry == null ? null : readEntry(ctx, idx, entry);
            Bitmap bm = png == null ? null : BitmapFactory.decodeByteArray(png, 0, png.length);
            if (bm != null) {
                int w = Math.min(8, bm.getWidth()), h = Math.min(8, bm.getHeight());
                long r = 0, g = 0, b = 0, n = 0;
                for (int y = 0; y < h; y += 2) for (int x = 0; x < w; x += 2) {
                    int px = bm.getPixel(x, y);
                    r += (px >> 16) & 255; g += (px >> 8) & 255; b += px & 255; n++;
                }
                if (n > 0) { c[0] = r / n / 255f; c[1] = g / n / 255f; c[2] = b / n / 255f; }
                bm.recycle();
            }
        } catch (Throwable ignored) { }
        return c;
    }

    private static byte[] readEntry(Context ctx, ModModelResolver.Idx idx, String entry) {
        // 索引里记了这张贴图出自哪个包，就直接开那一个，省掉把每个包从头扫一遍；
        // 万一没记录（条目名对不上等），再退回遍历所有包。
        Uri known = idx.texUri.get(entry);
        if (known != null) {
            byte[] b = readFromZip(ctx, known, entry);
            if (b != null) return b;
        }
        for (Uri u : idx.uriList) {
            if (u == null || u.equals(known)) continue;
            byte[] b = readFromZip(ctx, u, entry);
            if (b != null) return b;
        }
        return null;
    }

    private static byte[] readFromZip(Context ctx, Uri uri, String entry) {
        try (InputStream in = ctx.getContentResolver().openInputStream(uri);
             ZipInputStream z = new ZipInputStream(new java.io.BufferedInputStream(in))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (entry.equals(e.getName())) {
                    ByteArrayOutputStream o = new ByteArrayOutputStream();
                    byte[] b = new byte[8192];
                    int n;
                    while ((n = z.read(b)) > 0) o.write(b, 0, n);
                    return o.toByteArray();
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }
}
