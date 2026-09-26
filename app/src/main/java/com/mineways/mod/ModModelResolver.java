package com.mineways.mod;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 模组资源索引（jar/zip → models/blockstates/textures 清单）与模型解析（variants/父模型/elements）。 */
public final class ModModelResolver {

    public static final class Quad {
        public float[] p = new float[12];
        // 与 faces() 的顶点顺序一一对应：左下 → 右下 → 右上 → 左上（整张贴图）。
        // generic() 里模型自带 uv 时会被同口径的值覆盖，两者必须上下一致，否则没写 uv 的面会上下颠倒。
        public float[] uv = {0, 0, 1, 0, 1, 1, 0, 1};
        public String tex;
    }

    public static final class Resolved {
        public final List<Quad> quads = new ArrayList<>();
        public final Set<String> tex = new java.util.HashSet<>();
        public String note = "";
    }

    public static final class Idx {
        public final Map<String, String> models = new HashMap<>();
        public final Map<String, String> states = new HashMap<>();
        /** 贴图引用（ns:block/foo.png）→ 压缩包里的条目名。 */
        public final Map<String, String> texEntry = new HashMap<>();
        /** 条目名 → 它所在的压缩包。取贴图字节时直接开这一个包，不必逐个包扫。 */
        public final Map<String, Uri> texUri = new HashMap<>();
        /** 打开过的压缩包（取字节时的兜底扫描用）。 */
        public final List<Uri> uriList = new ArrayList<>();
        public int jars;
    }

    private ModModelResolver() { }

    public static Idx index(Context ctx, List<Uri> uris) {
        Idx idx = new Idx();
        for (Uri u : uris) {
            if (u == null) continue;
            // 必须先登记来源 Uri：贴图是「按条目名记录、之后再去包里取字节」的，
            // 少了这一步 texEntry 里的条目名永远取不到内容，模组方块会全部退回占位色。
            idx.uriList.add(u);
            try (InputStream in = ctx.getContentResolver().openInputStream(u);
                 ZipInputStream z = new ZipInputStream(new BufferedInputStream(in))) {
                idx.jars++;
                ZipEntry e;
                while ((e = z.getNextEntry()) != null) {
                    String n = e.getName();
                    if (e.isDirectory() || !n.startsWith("assets/") || !n.endsWith(".json") && !n.endsWith(".png"))
                        continue;
                    String rest = n.substring("assets/".length());
                    int si = rest.indexOf('/');
                    if (si <= 0) continue;
                    String ns = rest.substring(0, si);
                    String rel = rest.substring(si + 1);
                    if (rel.startsWith("models/block/") && rel.endsWith(".json")) {
                        // 键要保留 "block/" 这一级：blockstate 里引用的是 "ns:block/xxx"。
                        // 少这一级（原来取 rel.substring(13)）会让键变成 "ns:xxx"，与引用对不上，
                        // 模型永远查不到 → 整个模组方块都退化成占位色立方体。
                        idx.models.put(ns + ":" + rel.substring("models/".length(), rel.length() - 5), readString(z));
                    } else if (rel.startsWith("blockstates/") && rel.endsWith(".json")) {
                        idx.states.put(ns + ":" + rel.substring(12, rel.length() - 5), readString(z));
                    } else if (rel.startsWith("textures/") && rel.endsWith(".png")) {
                        idx.texEntry.put(ns + ":" + rel.substring(9), n);
                        idx.texUri.put(n, u);
                    }
                }
            } catch (Throwable ignored) { }
        }
        return idx;
    }

    public static String readString(ZipInputStream z) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = z.read(b)) > 0) o.write(b, 0, n);
        return new String(o.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 解析一个方块状态 → 网格。 */
    public static Resolved resolve(String state, Idx idx) {
        Resolved rd = new Resolved();
        try {
            String name = state.contains("[") ? state.substring(0, state.indexOf('[')) : state;
            String props = state.contains("[") ? state.substring(state.indexOf('[') + 1, state.lastIndexOf(']')) : "";
            Map<String, String> pv = parseProps(props);
            String modelPath = strip(name);
            int rx = 0, ry = 0;
            String bsJson = idx.states.get(name);
            if (bsJson == null) bsJson = idx.states.get(strip(name));
            if (bsJson != null) {
                JSONObject bs = new JSONObject(bsJson);
                JSONObject variants = bs.optJSONObject("variants");
                if (variants != null) {
                    String bestModel = null;
                    int bestScore = -1, brx = 0, bry = 0;
                    for (java.util.Iterator<String> it = variants.keys(); it.hasNext(); ) {
                        String k = it.next();
                        JSONObject v = variants.optJSONObject(k);
                        if (v == null) {
                            JSONArray arr = variants.optJSONArray(k);
                            v = arr == null ? null : arr.optJSONObject(0);
                        }
                        if (v == null) continue;
                        // 变体语义与 MC 一致：键里列出的属性必须**全部**命中才算这个变体，
                        // 命中数多的更具体、优先。只统计命中数会让 facing=north 这种「只对上一条」
                        // 的变体压过真正的默认变体，模型就选错了。
                        int score = 0;
                        for (String pair : k.split(",")) {
                            if (pair.isEmpty()) continue;
                            String[] kv = pair.split("=", 2);
                            if (kv.length != 2) continue;
                            String want = pv.get(kv[0].trim());
                            if (want == null || !want.equals(kv[1].trim())) { score = -1; break; }
                            score++;
                        }
                        if (score > bestScore) {
                            bestScore = score;
                            bestModel = v.optString("model");
                            brx = v.optInt("x", 0);
                            bry = v.optInt("y", 0);
                        }
                    }
                    if (bestModel != null && !bestModel.isEmpty()) { modelPath = bestModel; rx = brx; ry = bry; }
                } else if (bs.has("multipart")) {
                    rd.note = "multipart";
                }
            }
            if (modelPath == null) return rd;

            Map<String, String> tex = new HashMap<>();
            JSONObject model = load(modelPath, idx, tex, 0);
            if (model == null) return rd;
            String parent = model.optString("parent", "");
            String pShort = parent.contains("/") ? parent.substring(parent.lastIndexOf('/') + 1) : parent;
            JSONArray elements = model.optJSONArray("elements");

            if (elements != null && elements.length() > 0) {
                generic(model, tex, rd);
            } else if ("cube_all".equals(pShort)) {
                cube(rd, t(tex, "all"), t(tex, "all"), t(tex, "all"), t(tex, "all"), t(tex, "all"), t(tex, "all"));
            } else if ("cube".equals(pShort)) {
                cube(rd, t(tex, "north"), t(tex, "south"), t(tex, "east"), t(tex, "west"), t(tex, "up"), t(tex, "down"));
            } else if ("cube_column".equals(pShort)) {
                cube(rd, t(tex, "side"), t(tex, "side"), t(tex, "side"), t(tex, "side"), t(tex, "end"), t(tex, "end"));
            } else if ("cube_bottom_top".equals(pShort)) {
                cube(rd, t(tex, "side"), t(tex, "side"), t(tex, "side"), t(tex, "side"), t(tex, "top"), t(tex, "bottom"));
            } else if ("orientable".equals(pShort)) {
                cube(rd, t(tex, "front"), t(tex, "side"), t(tex, "side"), t(tex, "side"), t(tex, "top"), t(tex, "side"));
            } else if ("cross".equals(pShort)) {
                cross(rd, t(tex, "cross"));
            } else if (!parent.isEmpty()) {
                String any = t(tex, "all") != null ? t(tex, "all") : firstTex(tex);
                cube(rd, any, any, any, any, any, any);
            } else {
                return rd;
            }
            if (rx != 0 || ry != 0) {
                for (Quad q : rd.quads) {
                    if (ry != 0) rotate(q.p, "y", ry, 0.5f, 0.5f, 0.5f);
                    if (rx != 0) rotate(q.p, "x", rx, 0.5f, 0.5f, 0.5f);
                }
            }
        } catch (Throwable ignored) { }
        return rd;
    }

    /**
     * 解析贴图引用 → 真正的贴图路径。
     * 两种写法都要吃得下：模型自带 faces 里写的是「#变量名」（generic 直接原样传进来），
     * 而 cube/cube_all 这些内置父模型是按「变量名」取的；tex 的值本身也可能是「#另一个变量」，
     * 所以要沿别名链一直走到具体路径。
     */
    private static String t(Map<String, String> tex, String ref) {
        if (ref == null) return null;
        String v = ref.startsWith("#") ? tex.get(ref.substring(1)) : tex.get(ref);
        int g = 0;
        while (v != null && v.startsWith("#") && g++ < 8) v = tex.get(v.substring(1));
        return v;
    }

    /** 没有 all/side 这类约定键时的兜底：取第一个能落到具体路径的贴图值。 */
    private static String firstTex(Map<String, String> tex) {
        for (String v : tex.values()) {
            String r = v != null && v.startsWith("#") ? t(tex, v) : v;
            if (r != null && !r.isEmpty()) return r;
        }
        return null;
    }

    /**
     * 读模型并按 MC 的规则合并父链：贴图子覆盖父（父链上的只补自己没有的键）；
     * elements 是整体继承的 —— 自己没写就用父链上第一个写了 elements 的模型，一并返回。
     */
    private static JSONObject load(String path, Idx idx, Map<String, String> tex, int depth) {
        if (depth > 8 || path == null || path.isEmpty()) return null;
        String json = getModel(idx, path);
        if (json == null) return null;
        try {
            JSONObject m = new JSONObject(json);
            JSONObject pt = m.optJSONObject("textures");
            if (pt != null) {
                java.util.Iterator<String> it = pt.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    if (!tex.containsKey(k)) tex.put(k, pt.optString(k));
                }
            }
            String parent = m.optString("parent", "");
            JSONObject p = parent.isEmpty() ? null : load(parent, idx, tex, depth + 1);
            if (m.optJSONArray("elements") == null && p != null && p.optJSONArray("elements") != null)
                return p;
            return m;
        } catch (Throwable ignored) { return null; }
    }

    /** 模型查找：原样 → 去掉 minecraft: 前缀 → 补上 minecraft:（父链里两种写法都常见）。 */
    private static String getModel(Idx idx, String path) {
        if (path == null || path.isEmpty()) return null;
        String json = idx.models.get(path);
        if (json == null) json = idx.models.get(strip(path));
        if (json == null && !path.contains(":")) json = idx.models.get("minecraft:" + path);
        return json;
    }

    private static void cube(Resolved rd, String n, String s, String e, String w, String u, String d) {
        float[][] f = faces();
        String[] tv = {n, s, e, w, u, d};
        for (int i = 0; i < 6; i++) {
            if (tv[i] == null) continue;
            Quad q = new Quad();
            System.arraycopy(f[i], 0, q.p, 0, 12);
            q.tex = tv[i];
            rd.quads.add(q);
            rd.tex.add(tv[i]);
        }
    }

    private static void cross(Resolved rd, String tex) {
        if (tex == null) return;
        rd.tex.add(tex);
        float[][] c = {{0.1464f, 0.1464f, 0.8536f, 0.8536f}, {0.8536f, 0.1464f, 0.1464f, 0.8536f}};
        for (float[] pair : c) {
            Quad q = new Quad();
            q.p = new float[]{pair[0], 0, pair[1], pair[2], 0, pair[3], pair[2], 1, pair[3], pair[0], 1, pair[1]};
            q.tex = tex;
            rd.quads.add(q);
            Quad back = new Quad();
            back.p = new float[]{q.p[6], q.p[7], q.p[8], q.p[3], q.p[4], q.p[5], q.p[0], q.p[1], q.p[2], q.p[9], q.p[10], q.p[11]};
            back.tex = tex;
            rd.quads.add(back);
        }
    }

    private static void generic(JSONObject model, Map<String, String> tex, Resolved rd) {
        try {
            JSONArray el = model.getJSONArray("elements");
            for (int i = 0; i < el.length(); i++) {
                JSONObject e = el.getJSONObject(i);
                JSONArray from = e.getJSONArray("from"), to = e.getJSONArray("to");
                float fx = (float) (from.getDouble(0) / 16), fy = (float) (from.getDouble(1) / 16), fz = (float) (from.getDouble(2) / 16);
                float tx = (float) (to.getDouble(0) / 16), ty = (float) (to.getDouble(1) / 16), tz = (float) (to.getDouble(2) / 16);
                JSONObject faces = e.optJSONObject("faces");
                if (faces == null) continue;
                JSONObject rot = e.optJSONObject("rotation");
                float angle = rot == null ? 0 : (float) rot.optDouble("angle", 0);
                String axis = rot == null ? "y" : rot.optString("axis", "y");
                float ox = 0.5f, oy = 0.5f, oz = 0.5f;
                if (rot != null && rot.has("origin")) {
                    JSONArray o = rot.getJSONArray("origin");
                    ox = (float) (o.getDouble(0) / 16); oy = (float) (o.getDouble(1) / 16); oz = (float) (o.getDouble(2) / 16);
                }
                for (String fk : new String[]{"north", "south", "east", "west", "up", "down"}) {
                    JSONObject face = faces.optJSONObject(fk);
                    if (face == null) continue;
                    String tv = t(tex, face.optString("texture"));
                    if (tv == null) continue;
                    JSONArray uvj = face.optJSONArray("uv");
                    Quad q = new Quad();
                    if (uvj != null && uvj.length() == 4) {
                        float u0 = (float) (uvj.getDouble(0) / 16), v0 = (float) (uvj.getDouble(1) / 16);
                        float u1 = (float) (uvj.getDouble(2) / 16), v1 = (float) (uvj.getDouble(3) / 16);
                        q.uv = new float[]{u0, 1 - v1, u1, 1 - v1, u1, 1 - v0, u0, 1 - v0};
                    }
                    q.p = faceVerts(fk, fx, fy, fz, tx, ty, tz);
                    q.tex = tv;
                    if (angle != 0) rotate(q.p, axis, angle, ox, oy, oz);
                    rd.quads.add(q);
                    rd.tex.add(tv);
                }
            }
        } catch (Throwable ignored) { }
    }

    private static float[][] faces() {
        // 每个面四个顶点，顺序统一为「从方块的外面看：贴图左下 → 右下 → 右上 → 左上」。
        // 这个顺序同时决定两件事，不能随手写：
        //   ① 环绕方向（右手定则的法线必须朝外，否则整个模组方块会被渲染成内外翻转）；
        //   ② 贴图方向（u 向右、v 向下，与下面 generic() 里显式 uv 的算法同源）。
        return new float[][]{
            {1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0},  // north (-Z)
            {0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1},  // south (+Z)
            {1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1},  // east (+X)
            {0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0},  // west (-X)
            {0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0},  // up (+Y)
            {0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1}   // down (-Y)
        };
    }

    private static float[] faceVerts(String face, float fx, float fy, float fz, float tx, float ty, float tz) {
        int idx = "north".equals(face) ? 0 : "south".equals(face) ? 1 : "east".equals(face) ? 2
                : "west".equals(face) ? 3 : "up".equals(face) ? 4 : 5;
        float[] c = faces()[idx];
        float sx = tx - fx, sy = ty - fy, sz = tz - fz;
        return new float[]{
            fx + c[0] * sx, fy + c[1] * sy, fz + c[2] * sz,
            fx + c[3] * sx, fy + c[4] * sy, fz + c[5] * sz,
            fx + c[6] * sx, fy + c[7] * sy, fz + c[8] * sz,
            fx + c[9] * sx, fy + c[10] * sy, fz + c[11] * sz};
    }

    private static void rotate(float[] p, String axis, float deg, float ox, float oy, float oz) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        for (int i = 0; i < 12; i += 3) {
            float x = p[i] - ox, y = p[i + 1] - oy, z = p[i + 2] - oz;
            if ("y".equals(axis)) { p[i] = (float) (x * c + z * s) + ox; p[i + 2] = (float) (-x * s + z * c) + oz; }
            else if ("x".equals(axis)) { p[i + 1] = (float) (y * c - z * s) + oy; p[i + 2] = (float) (y * s + z * c) + oz; }
            else { p[i] = (float) (x * c - y * s) + ox; p[i + 1] = (float) (x * s + y * c) + oy; }
        }
    }

    private static Map<String, String> parseProps(String props) {
        Map<String, String> m = new HashMap<>();
        if (props == null || props.isEmpty()) return m;
        for (String pair : props.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) m.put(kv[0].trim(), kv[1].trim());
        }
        return m;
    }

    private static String strip(String s) {
        return s == null ? "" : (s.startsWith("minecraft:") ? s.substring(10) : s);
    }
}
