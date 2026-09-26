package com.mineways.pixelart;
// ============================================================================
// PixelArtConverter.java — 纯色背景像素图 → Blockbench .bbmodel + OBJ/MTL
// 移植自 png2bbmodel.py / convert_batch.py（2026-09-25 交付版），逐函数对齐。
// 运行环境：纯 JDK / Android 通用，无第三方依赖。
//
// 与 Python 版的三处刻意差异（其余逻辑逐行等价）：
//   1. vt 的 u 分量归一化：Python 版 build_obj 漏除 lw（v 除以了 lh，u 未除），
//      会导致 OBJ 在 Blender 里贴图横向重复。本版按 u/lw 修正，Python 版已同步修复。
//   2. assert → check()：Android 侧 assert 默认关闭，改为显式校验 + IllegalStateException。
//   3. PNG 编解码：读取侧由调用方提供 int[][] 像素矩阵（Android 用 Bitmap，见
//      PixelArtAndroidIO.java）；编码侧本文件自带手写 PNG encoder（JDK Deflater，
//      与 Python 版手写 encoder 同构：filter=0 + zlib level 9），保持零依赖设计。
//
// 坐标/UV 约定（与 Python 版一致）：
//   - bbmodel 几何：from_y = H-1-y（图像行 0 → 模型顶部），to_y = H-y
//   - bbmodel UV：  [x, y, x+1, y+1]，不翻转（v 轴与贴图同向）
//   - OBJ vt：      u/lw，1 - v/lh（OBJ 纹理原点在左下，恰好与 bbmodel 相反）
// ============================================================================
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

public final class PixelArtConverter {

    private PixelArtConverter() {}

    // ------------------------------------------------------------------
    // 结果类型
    // ------------------------------------------------------------------

    /** 背景检测结果（对应 Python detect_background 的三元组返回） */
    public static final class BgResult {
        public final int bg;        // ARGB 背景色
        public final boolean solid; // 边缘环占比 >= 0.6
        public final double ratio;
        BgResult(int bg, boolean solid, double ratio) { this.bg = bg; this.solid = solid; this.ratio = ratio; }
    }

    /** 一个前景像素（对应 Python 的 (x, y, rgba) 元组） */
    public static final class Pixel {
        public final int x, y, argb;
        public Pixel(int x, int y, int argb) { this.x = x; this.y = y; this.argb = argb; }
    }

    /** 转换产物（bbmodel JSON 文本 / OBJ 文本 / MTL 文本 / 贴图 PNG 字节） */
    public static final class ConvertResult {
        public String bbmodelJson;
        public String obj;
        public String mtl;
        public byte[] texturePng;
        public int lw, lh;   // 逻辑分辨率（聚合后）
        public int cubes;    // 前景像素数 == cube 数
        public double bgEdgeRatio;
    }

    // ------------------------------------------------------------------
    // 颜色距离（png2bbmodel.py L88-89：只比 RGB 三通道，不含 alpha）
    // ------------------------------------------------------------------

    public static int dist2(int c1, int c2) {
        int dr = ((c1 >>> 16) & 0xFF) - ((c2 >>> 16) & 0xFF);
        int dg = ((c1 >>> 8) & 0xFF) - ((c2 >>> 8) & 0xFF);
        int db = (c1 & 0xFF) - (c2 & 0xFF);
        return dr * dr + dg * dg + db * db;
    }

    // ------------------------------------------------------------------
    // 背景检测（png2bbmodel.py L91-98）
    // 四角众数定背景色；边缘环 = 顶行+底行+左列+右列（四角各计入两次，ring 长 2w+2h）；
    // ring 中 dist2<=tol² 的占比 >= 0.6 判定纯色背景成立。
    // ------------------------------------------------------------------

    public static BgResult detectBackground(int[][] px, int w, int h, int tol) {
        int[] corners = { px[0][0], px[0][w - 1], px[h - 1][0], px[h - 1][w - 1] };
        int bg = mode(corners);
        int hit = 0, ringLen = 2 * w + 2 * h;
        for (int x = 0; x < w; x++) {
            if (dist2(px[0][x], bg) <= tol * tol) hit++;
            if (dist2(px[h - 1][x], bg) <= tol * tol) hit++;
        }
        for (int y = 0; y < h; y++) {
            if (dist2(px[y][0], bg) <= tol * tol) hit++;
            if (dist2(px[y][w - 1], bg) <= tol * tol) hit++;
        }
        double ratio = (double) hit / ringLen;
        return new BgResult(bg, ratio >= 0.6, ratio);
    }

    /** 对应 Python max(set(corners), key=corners.count)：按出现次数取众数 */
    private static int mode(int[] a) {
        int best = a[0], bestCount = -1;
        for (int i = 0; i < a.length; i++) {
            int n = 0;
            for (int j = 0; j < a.length; j++) if (a[j] == a[i]) n++;
            if (n > bestCount) { bestCount = n; best = a[i]; }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // 前景提取（png2bbmodel.py L100-104）：行优先，与 Python 双层循环顺序一致
    // ------------------------------------------------------------------

    public static List<Pixel> extractForeground(int[][] px, int w, int h, int bg, int tol) {
        int t2 = tol * tol;
        List<Pixel> out = new ArrayList<>();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (dist2(px[y][x], bg) > t2) out.add(new Pixel(x, y, px[y][x]));
        return out;
    }

    // ------------------------------------------------------------------
    // 逻辑像素块聚合（convert_batch.py L22-35）
    // blk×blk 块内颜色必须唯一（硬断言），任何混色块即失败——精度优先，绝不猜色。
    // ------------------------------------------------------------------

    public static int[][] aggregateToLogical(int[][] px, int w, int h, int blk) {
        check(w % blk == 0 && h % blk == 0, "尺寸 " + w + "x" + h + " 不是 " + blk + " 的整数倍");
        int lw = w / blk, lh = h / blk;
        int[][] out = new int[lh][lw];
        for (int by = 0; by < lh; by++) {
            for (int bx = 0; bx < lw; bx++) {
                int first = px[by * blk][bx * blk];
                for (int oy = 0; oy < blk; oy++) {
                    for (int ox = 0; ox < blk; ox++) {
                        int c = px[by * blk + oy][bx * blk + ox];
                        check(c == first, "混色块 (" + bx + "," + by + "): " + hex(first) + " vs " + hex(c));
                    }
                }
                out[by][bx] = first;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // bbmodel 构建（png2bbmodel.py L107-153）
    // 手写紧凑 JSON 序列化（无空格分隔，同 Python json.dump(separators=(",",":"))），
    // 字段顺序与 Python dict 插入序完全一致。
    // ------------------------------------------------------------------

    public static BbOut buildBbmodel(List<Pixel> fg, int w, int h, double thickness, String name, byte[] texturePng) {
        String texUuid = UUID.randomUUID().toString();
        String texB64 = Base64Impl.encodeToString(texturePng);
        StringBuilder sb = new StringBuilder(1 << 20);
        sb.append("{\"meta\":{\"format_version\":\"4.5\",\"model_format\":\"free\",\"box_uv\":false}")
          .append(",\"name\":\"").append(esc(name)).append('"')
          .append(",\"model_identifier\":\"\"")
          .append(",\"visible_box\":[1,1,0]")
          .append(",\"variable_placeholders\":\"\",\"variable_placeholder_buttons\":[]")
          .append(",\"timeline_setups\":[],\"unhandled_root_fields\":{}")
          .append(",\"resolution\":{\"width\":").append(w).append(",\"height\":").append(h).append('}')
          .append(",\"elements\":[");
        List<String> uuids = new ArrayList<>(fg.size());
        List<Cube> cubes = new ArrayList<>(fg.size());
        boolean first = true;
        for (Pixel p : fg) {
            if (!first) sb.append(',');
            first = false;
            String u = UUID.randomUUID().toString();
            uuids.add(u);
            int gy = h - 1 - p.y; // 几何 y 翻转：图像行 0(顶) → 模型顶(H-1)，底行 → 0
            Cube c = new Cube();
            c.name = "px_" + p.x + "_" + p.y;
            c.from = new double[]{p.x, gy, 0.0};
            c.to = new double[]{p.x + 1, gy + 1, thickness};
            c.uv = new int[]{p.x, p.y, p.x + 1, p.y + 1}; // UV 不翻转，颜色跟随原像素
            cubes.add(c);
            sb.append("{\"name\":\"px_").append(p.x).append('_').append(p.y).append('"')
              .append(",\"box_uv\":false,\"rescale\":false,\"locked\":false")
              .append(",\"render_order\":\"default\",\"allow_mirror_modeling\":true")
              .append(",\"from\":[").append(p.x).append(',').append(gy).append(",0.0]")
              .append(",\"to\":[").append(p.x + 1).append(',').append(gy + 1).append(',').append(num(thickness)).append(']')
              .append(",\"origin\":[0,0,0]")
              .append(",\"autouv\":0,\"color\":0")
              .append(",\"faces\":{")
              .append(faceJson("north", p, texUuid)).append(',')
              .append(faceJson("east", p, texUuid)).append(',')
              .append(faceJson("south", p, texUuid)).append(',')
              .append(faceJson("west", p, texUuid)).append(',')
              .append(faceJson("up", p, texUuid)).append(',')
              .append(faceJson("down", p, texUuid))
              .append("},\"type\":\"cube\",\"uuid\":\"").append(u).append("\"}");
        }
        sb.append("],\"outliner\":[");
        for (int i = 0; i < uuids.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(uuids.get(i)).append('"');
        }
        sb.append("],\"textures\":[{")
          .append("\"path\":\"\",\"name\":\"").append(esc(name)).append(".png\",\"folder\":\"\",\"namespace\":\"\"")
          .append(",\"id\":\"0\",\"group\":\"\",\"width\":").append(w).append(",\"height\":").append(h)
          .append(",\"uv_width\":").append(w).append(",\"uv_height\":").append(h)
          .append(",\"particle\":false,\"use_as_default\":false")
          .append(",\"layers_enabled\":false,\"sync_to_project\":\"\"")
          .append(",\"render_mode\":\"default\",\"render_sides\":\"auto\"")
          .append(",\"frame_time\":1,\"frame_order_type\":\"loop\"")
          .append(",\"frame_order\":\"\",\"frame_interpolate\":false")
          .append(",\"visible\":true,\"internal\":true,\"saved\":false")
          .append(",\"uuid\":\"").append(texUuid).append('"')
          .append(",\"relative_path\":\"\"")
          .append(",\"source\":\"data:image/png;base64,").append(texB64).append('"')
          .append("}]}");
        BbOut out = new BbOut();
        out.json = sb.toString();
        out.cubes = cubes;
        return out;
    }

    /** bbmodel element 的结构化表示（对应 Python build_obj 读的 e["from"]/e["to"]/e["faces"]） */
    public static final class Cube {
        public String name;
        public double[] from, to;
        public int[] uv; // 六面同值（Python face_uv 为同一列表引用）
    }

    public static final class BbOut {
        public String json;
        public List<Cube> cubes;
    }

    /** face 键序与 Python 元组 ("north","east","south","west","up","down") 迭代序一致 */
    private static String faceJson(String side, Pixel p, String texUuid) {
        return "\"" + side + "\":{\"uv\":[" + p.x + ',' + p.y + ',' + (p.x + 1) + ',' + (p.y + 1)
                + "],\"texture\":\"" + texUuid + "\"}";
    }

    // ------------------------------------------------------------------
    // 手写 PNG encoder（对应 png2bbmodel.py _png_from_px L155-166）
    // 透明底 + 前景原色平铺，filter=0，zlib level 9。语义与 Python 版一致。
    // ------------------------------------------------------------------

    public static byte[] encodeTexturePng(int w, int h, List<Pixel> fg) {
        int[] grid = new int[w * h]; // 默认 0x00000000 全透明，同 Python [(0,0,0,0)]*w*h
        for (Pixel p : fg) grid[p.y * w + p.x] = p.argb;
        byte[] raw = new byte[h * (w * 4 + 1)];
        int o = 0;
        for (int y = 0; y < h; y++) {
            raw[o++] = 0; // filter type 0
            for (int x = 0; x < w; x++) {
                int c = grid[y * w + x];
                raw[o++] = (byte) ((c >>> 16) & 0xFF); // R
                raw[o++] = (byte) ((c >>> 8) & 0xFF);  // G
                raw[o++] = (byte) (c & 0xFF);          // B
                raw[o++] = (byte) ((c >>> 24) & 0xFF); // A
            }
        }
        // zlib stream：2 字节头 + deflate(raw, level 9) + Adler-32 尾
        Deflater def = new Deflater(9, true); // nowrap：不含 zlib 头尾，自行拼接
        def.setInput(raw);
        def.finish();
        byte[] comp = new byte[raw.length * 2 + 64];
        int clen = 0;
        while (!def.finished()) {
            clen += def.deflate(comp, clen, comp.length - clen);
            if (clen == comp.length) throw new IllegalStateException("deflate 溢出");
        }
        def.end();
        byte[] idat = new byte[clen + 6];
        idat[0] = 0x78; idat[1] = (byte) 0xDA; // CMF/FLG：32K 窗口 + level 9
        System.arraycopy(comp, 0, idat, 2, clen);
        putUInt32(idat, 2 + clen, (int) adler32(raw));

        return concat(PNG_SIGNATURE,
                chunk("IHDR", ihdr(w, h)),
                chunk("IDAT", idat),
                chunk("IEND", new byte[0]));
    }

    private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };

    private static byte[] ihdr(int w, int h) {
        byte[] b = new byte[13];
        putUInt32(b, 0, w);
        putUInt32(b, 4, h);
        b[8] = 8;  // bit depth
        b[9] = 6;  // color type RGBA
        // b[10..12] = 0：compression=0, filter=0, interlace=0
        return b;
    }

    private static byte[] chunk(String type, byte[] data) {
        byte[] out = new byte[12 + data.length];
        putUInt32(out, 0, data.length);
        for (int i = 0; i < 4; i++) out[4 + i] = (byte) type.charAt(i);
        System.arraycopy(data, 0, out, 8, data.length);
        CRC32 crc = new CRC32();
        crc.update(out, 4, 4 + data.length);
        putUInt32(out, 8 + data.length, (int) crc.getValue());
        return out;
    }

    private static byte[] concat(byte[]... arrs) {
        int len = 0;
        for (byte[] a : arrs) len += a.length;
        byte[] out = new byte[len];
        int o = 0;
        for (byte[] a : arrs) { System.arraycopy(a, 0, out, o, a.length); o += a.length; }
        return out;
    }

    private static void putUInt32(byte[] b, int off, int v) {
        b[off] = (byte) (v >>> 24); b[off + 1] = (byte) (v >>> 16);
        b[off + 2] = (byte) (v >>> 8); b[off + 3] = (byte) v;
    }

    /** zlib 尾部 Adler-32（Deflater nowrap 模式不含） */
    private static long adler32(byte[] data) {
        long a = 1, b = 0;
        for (byte t : data) {
            a = (a + (t & 0xFF)) % 65521;
            b = (b + a) % 65521;
        }
        return (b << 16) | a;
    }

    // ------------------------------------------------------------------
    // OBJ/MTL 生成（convert_batch.py L37-68，vt u 归一化已修正为 u/lw）
    // 输出行序与 Python 一致：注释 → v×8n → vt×24n → vn×6n → 空行 → o → usemtl → f×6n
    // vn 每 face 一条（共 6n 条，f 行用 v/vt//vn 三段式引用）。
    // ------------------------------------------------------------------

    // Python: def build_obj(elements, lw, lh, texname) — 从 bbmodel elements 读数，逐行同构。
    // vt 修正：u/lw 与 1-v/lh 都归一化（Python 版漏除 lw 的 bug 已在两侧同步修复）。
    public static String[] buildObj(List<Cube> elements, int lw, int lh, String texname) {
        // 与 Python quad dict 字面序一致（f/vt/vn 的循环序都按它排）
        String[] faceOrder = {"north", "south", "west", "east", "up", "down"};
        int[][] quads = { {0,3,2,1}, {4,5,6,7}, {0,4,7,3}, {1,2,6,5}, {3,7,6,2}, {0,1,5,4} };
        int[][] normals = { {0,0,-1}, {0,0,1}, {-1,0,0}, {1,0,0}, {0,1,0}, {0,-1,0} };

        List<String> vLines  = new ArrayList<>(elements.size() * 8);
        List<String> vtLines = new ArrayList<>(elements.size() * 24);
        List<String> vnLines = new ArrayList<>(elements.size() * 6);
        List<String> fLines  = new ArrayList<>(elements.size() * 6);

        int fi = 0;
        for (Cube e : elements) {
            double x1 = e.from[0], y1 = e.from[1], z1 = e.from[2];
            double x2 = e.to[0],   y2 = e.to[1],   z2 = e.to[2];
            int base = vLines.size();
            // 8 角序与 Python 完全一致：前 4 个 z1 面、后 4 个 z2 面
            double[][] corners = {
                {x1,y1,z1},{x2,y1,z1},{x2,y2,z1},{x1,y2,z1},
                {x1,y1,z2},{x2,y1,z2},{x2,y2,z2},{x1,y2,z2} };
            for (double[] c : corners)
                vLines.add(String.format(Locale.US, "v %.4f %.4f %.4f", c[0], c[1], c[2]));

            int u1 = e.uv[0], v1 = e.uv[1], u2 = e.uv[2], v2 = e.uv[3];
            for (int s = 0; s < 6; s++) {
                int[] idx = quads[s];
                int cu = vtLines.size();
                // OBJ vt 原点在左下 → v 翻转；u/v 都归一化
                double[][] cUv = {
                    {(double) u1 / lw, 1 - (double) v1 / lh},
                    {(double) u2 / lw, 1 - (double) v1 / lh},
                    {(double) u2 / lw, 1 - (double) v2 / lh},
                    {(double) u1 / lw, 1 - (double) v2 / lh} };
                for (double[] uv : cUv)
                    vtLines.add(String.format(Locale.US, "vt %.6f %.6f", uv[0], uv[1]));
                int[] nn = normals[s];
                vnLines.add("vn " + nn[0] + ' ' + nn[1] + ' ' + nn[2]);
                fLines.add("f " + (base + idx[0] + 1) + '/' + (cu + 1) + "//" + (fi + 1)
                         + ' ' + (base + idx[1] + 1) + '/' + (cu + 2) + "//" + (fi + 1)
                         + ' ' + (base + idx[2] + 1) + '/' + (cu + 3) + "//" + (fi + 1)
                         + ' ' + (base + idx[3] + 1) + '/' + (cu + 4) + "//" + (fi + 1));
                fi++;
            }
        }
        // 行结构与 Python 一致：注释 → v → vt → vn → 空行 → o → usemtl → f
        List<String> lines = new ArrayList<>();
        lines.add("# pixel-art to obj | cubes=" + elements.size() + " | tex=" + texname);
        lines.addAll(vLines);
        lines.addAll(vtLines);
        lines.addAll(vnLines);
        lines.add("");
        lines.add("o pixel_art");
        lines.add("usemtl " + texname);
        lines.addAll(fLines);
        String obj = String.join("\n", lines) + "\n";
        String mtl = "newmtl " + texname + "\nKa 1.0 1.0 1.0\nKd 1.0 1.0 1.0\nKs 0.0 0.0 0.0\n"
                   + "Ns 10.0\nd 1.0\nillum 2\nmap_Kd " + texname + ".png\n";
        return new String[]{obj, mtl};
    }

    /** 对应 convert_batch.py L66-67，逐字符一致 */
    public static String buildMtl(String texname) {
        return "newmtl " + texname + "\n"
                + "Ka 1.0 1.0 1.0\n"
                + "Kd 1.0 1.0 1.0\n"
                + "Ks 0.0 0.0 0.0\n"
                + "Ns 10.0\nd 1.0\nillum 2\n"
                + "map_Kd " + texname + ".png\n";
    }

    // ------------------------------------------------------------------
    // 主流程（convert_batch.py L70-99：聚合 → 检测 → 提取 → 构建 → 断言三连）
    // ------------------------------------------------------------------

    public static ConvertResult convert(int[][] px, int w, int h, int blk, int tol,
                                        double thickness, String name) {
        check(w % blk == 0 && h % blk == 0, "尺寸 " + w + "x" + h + " 不是 " + blk + " 的整数倍");
        int lw = w / blk, lh = h / blk;
        int[][] logical = aggregateToLogical(px, w, h, blk);

        BgResult bgRes = detectBackground(logical, lw, lh, tol);
        check(bgRes.solid, name + ": 背景非纯色（边缘占比 " + Math.round(bgRes.ratio * 100) + "%）");
        List<Pixel> fg = extractForeground(logical, lw, lh, bgRes.bg, tol);

        byte[] texPng = encodeTexturePng(lw, lh, fg);
        BbOut bb = buildBbmodel(fg, lw, lh, thickness, name, texPng);

        // 断言 1：cube 数对账（== 前景像素数 == 总像素 - 背景像素，tol²=144 同 Python 字面值）
        int nBg = 0, t2 = tol * tol;
        for (int y = 0; y < lh; y++)
            for (int x = 0; x < lw; x++)
                if (dist2(logical[y][x], bgRes.bg) <= t2) nBg++;
        check(fg.size() == lw * lh - nBg, name + ": cube 数对不上");

        // 断言 2：OBJ 顶点/面对账（v=8n, f=6n）
        String[] om = buildObj(bb.cubes, lw, lh, "texture");
        
        check(countLineStart(om[0], "v ") == 8 * fg.size() && countLineStart(om[0], "f ") == 6 * fg.size(),
                name + ": OBJ 对账失败");

        ConvertResult r = new ConvertResult();
        r.bbmodelJson = bb.json;
        r.obj = om[0];
        r.mtl = om[1];
        r.texturePng = texPng;
        r.lw = lw; r.lh = lh;
        r.cubes = fg.size();
        r.bgEdgeRatio = bgRes.ratio;
        return r;
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** Python assert 的显式替代（Android release 默认关 assert，必须显式异常） */
    private static void check(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\b': b.append("\\b"); break;
                case '\f': b.append("\\f"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format(Locale.US, "\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.toString();
    }

    /** double → JSON 数字（与 Python json.dump 一致：1.0 输出 "1.0"、0.0 输出 "0.0"） */
    private static String num(double d) {
        if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) return (long) d + ".0";
        return Double.toString(d);
    }

    private static String hex(int argb) {
        return String.format(Locale.US, "0x%08X", argb);
    }

    /** 统计以 prefix 开头的行数（v 与 vt 都以 "v" 开头，必须含空格区分） */
    private static int countLineStart(String text, String prefix) {
        int n = 0, i = 0;
        while ((i = text.indexOf(prefix, i)) >= 0) {
            if (i == 0 || text.charAt(i - 1) == '\n') n++;
            i += 1;
        }
        return n;
    }

    /** 最小 Base64（RFC 4648 无换行）。Android 上也可换 android.util.Base64.NO_WRAP */
    private static final char[] B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    static final class Base64Impl {
        static String encodeToString(byte[] data) {
            StringBuilder sb = new StringBuilder((data.length + 2) / 3 * 4);
            for (int i = 0; i < data.length; i += 3) {
                int b0 = data[i] & 0xFF;
                int b1 = (i + 1 < data.length) ? data[i + 1] & 0xFF : 0;
                int b2 = (i + 2 < data.length) ? data[i + 2] & 0xFF : 0;
                int trip = (b0 << 16) | (b1 << 8) | b2;
                sb.append(B64[(trip >>> 18) & 0x3F]).append(B64[(trip >>> 12) & 0x3F]);
                sb.append(i + 1 < data.length ? B64[(trip >>> 6) & 0x3F] : '=');
                sb.append(i + 2 < data.length ? B64[trip & 0x3F] : '=');
            }
            return sb.toString();
        }
    }
}
