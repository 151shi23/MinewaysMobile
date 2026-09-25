package com.mineways.mod;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Inflater;

/** 模组支持·第一步：只读扫描选区内方块名+属性（现代 Anvil 调色板格式）。纯新增，仅在「模组转换」开启时调用。 */
public final class ModBlockScanner {

    public static final class Inst {
        public final String state;
        public final int x, y, z;

        Inst(String state, int x, int y, int z) { this.state = state; this.x = x; this.y = y; this.z = z; }
    }

    public static final class Result {
        public final List<Inst> instances = new ArrayList<>();
        public final Map<String, Integer> counts = new LinkedHashMap<>();
        public final List<String> notes = new ArrayList<>();
        // 原版方块的世界包围盒（用于与 OBJ 实测包围盒做坐标标定）
        public long vanillaCount;
        public double vminX = 1e18, vminY = 1e18, vminZ = 1e18;
        public double vmaxX = -1e18, vmaxY = -1e18, vmaxZ = -1e18;
    }

    private ModBlockScanner() { }

    public static Result scan(File worldDir, int dim, boolean newFormat,
                              int minx, int miny, int minz, int maxx, int maxy, int maxz) {
        Result res = new Result();
        File dir = dimensionDir(worldDir, dim, newFormat);
        int cx0 = Math.floorDiv(minx, 16), cx1 = Math.floorDiv(maxx, 16);
        int cz0 = Math.floorDiv(minz, 16), cz1 = Math.floorDiv(maxz, 16);
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++)
                try { scanChunk(dir, cx, cz, minx, miny, minz, maxx, maxy, maxz, res); }
                catch (Throwable t) { res.notes.add("区块(" + cx + "," + cz + ")读取失败：" + t); }
        return res;
    }

    private static File dimensionDir(File world, int dim, boolean nf) {
        File d = world;
        if (nf) {
            if (dim == 1) d = new File(d, "dimensions/minecraft/the_nether");
            else if (dim == 2) d = new File(d, "dimensions/minecraft/the_end");
            else d = new File(d, "dimensions/minecraft/overworld");
        } else if (dim == 1) d = new File(d, "DIM-1");
        else if (dim == 2) d = new File(d, "DIM1");
        return d;
    }

    private static void scanChunk(File dir, int cx, int cz, int minx, int miny, int minz,
                                  int maxx, int maxy, int maxz, Result res) throws Exception {
        File rf = new File(dir, "region/r." + Math.floorDiv(cx, 32) + "." + Math.floorDiv(cz, 32) + ".mca");
        if (!rf.isFile()) return;
        byte[] head = new byte[8192];
        try (InputStream in = new FileInputStream(rf)) { readFully(in, head); }
        int slot = Math.floorMod(cx, 32) + Math.floorMod(cz, 32) * 32;
        int o = ((head[slot * 4] & 255) << 16) | ((head[slot * 4 + 1] & 255) << 8) | (head[slot * 4 + 2] & 255);
        int sectors = head[slot * 4 + 3] & 255;
        if (o == 0 || sectors == 0) return;
        byte[] chunk;
        try (InputStream in = new FileInputStream(rf)) {
            long skip = 4096L * o, total = 0;
            while (total < skip) {
                long s = in.skip(skip - total);
                if (s <= 0) { if (in.read() < 0) return; s = 1; }
                total += s;
            }
            int len = readInt(in);
            int comp = in.read();
            if (len <= 1 || comp != 2) return;
            byte[] z = new byte[len - 1];
            readFully(in, z);
            chunk = inflate(z);
        }
        Nbt root = Nbt.parse(chunk);
        Nbt sections = root.get("sections");
        if (sections == null || sections.type != Nbt.LIST) return;
        for (Nbt sec : sections.items) {
            if (sec == null || sec.type != Nbt.COMPOUND) continue;
            Nbt yTag = sec.get("Y");
            int secY = yTag == null ? 0 : (int) yTag.asLong(0);
            Nbt bs = sec.get("block_states");
            if (bs == null || bs.type != Nbt.COMPOUND) continue;
            Nbt palette = bs.get("palette");
            if (palette == null || palette.type != Nbt.LIST || palette.items.isEmpty()) continue;
            int pn = palette.items.size();
            String[] names = new String[pn];
            String[] props = new String[pn];
            for (int p = 0; p < pn; p++) {
                Nbt e = palette.items.get(p);
                Nbt nm = e.get("Name");
                names[p] = nm == null ? "" : nm.asString("");
                Nbt pr = e.get("Properties");
                if (pr != null && pr.type == Nbt.COMPOUND && !pr.map.isEmpty()) {
                    List<String> ks = new ArrayList<>(pr.map.keySet());
                    Collections.sort(ks);
                    StringBuilder sb = new StringBuilder();
                    for (String k : ks) sb.append(k).append('=').append(pr.map.get(k).asString("")).append(',');
                    props[p] = sb.toString();
                }
            }
            Nbt data = bs.get("data");
            if (data == null || data.type != Nbt.LONGARRAY || data.longs.length == 0) {
                // 整段只有一种方块时 MC 会省略 data 数组：此时 4096 格全是 palette[0]，
                // 必须逐格铺开，只记一格会让整段模组方块塌成角上的一个立方体。
                for (int i = 0; i < 4096; i++)
                    emit(names[0], props[0], cx, cz, i % 16, i / 256, (i / 16) % 16, secY,
                            minx, miny, minz, maxx, maxy, maxz, res);
                continue;
            }
            long[] packed = data.longs;
            int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(Math.max(1, pn - 1)));
            int perLong = 64 / bits;
            long mask = (1L << bits) - 1;
            for (int i = 0; i < 4096; i++) {
                int idx = (perLong > 0 && (i / perLong) < packed.length)
                        ? (int) ((packed[i / perLong] >>> ((i % perLong) * bits)) & mask) : 0;
                if (idx < 0 || idx >= pn) continue;
                emit(names[idx], props[idx], cx, cz, i % 16, i / 256, (i / 16) % 16, secY,
                        minx, miny, minz, maxx, maxy, maxz, res);
            }
        }
    }

    private static void emit(String name, String props, int cx, int cz, int lx, int ly, int lz, int secY,
                             int minx, int miny, int minz, int maxx, int maxy, int maxz, Result res) {
        if (name == null || name.isEmpty()) return;
        int wx = cx * 16 + lx, wy = secY * 16 + ly, wz = cz * 16 + lz;
        if (wx < minx || wx > maxx || wz < minz || wz > maxz || wy < miny || wy > maxy) return;
        if (name.startsWith("minecraft:")) {
            if (name.equals("minecraft:air") || name.equals("minecraft:cave_air")
                    || name.equals("minecraft:void_air")) return;
            res.vanillaCount++;
            res.vminX = Math.min(res.vminX, wx); res.vmaxX = Math.max(res.vmaxX, wx);
            res.vminY = Math.min(res.vminY, wy); res.vmaxY = Math.max(res.vmaxY, wy);
            res.vminZ = Math.min(res.vminZ, wz); res.vmaxZ = Math.max(res.vmaxZ, wz);
            return;
        }
        String key = name + (props != null ? "[" + props + "]" : "");
        res.instances.add(new Inst(key, wx, wy, wz));
        Integer c = res.counts.get(key);
        res.counts.put(key, c == null ? 1 : c + 1);
    }

    private static void readFully(InputStream in, byte[] buf) throws Exception {
        int off = 0, n;
        while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) off += n;
    }

    private static int readInt(InputStream in) throws Exception {
        int b0 = in.read(), b1 = in.read(), b2 = in.read(), b3 = in.read();
        if ((b0 | b1 | b2 | b3) < 0) return 0;
        return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    private static byte[] inflate(byte[] src) throws Exception {
        Inflater inf = new Inflater();
        inf.setInput(src);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        try {
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0) { if (inf.needsInput() || inf.needsDictionary()) break; }
                else out.write(buf, 0, n);
            }
        } finally { inf.end(); }
        return out.toByteArray();
    }

    /** 极简大端 NBT（只读）。 */
    public static final class Nbt {
        public static final int END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6,
                BYTEARRAY = 7, STRING = 8, LIST = 9, COMPOUND = 10, INTARRAY = 11, LONGARRAY = 12;
        public final int type;
        public final long num;
        public final String str;
        public final List<Nbt> items;
        public final Map<String, Nbt> map;
        public final long[] longs;

        Nbt(int type, long num, String str, List<Nbt> items, Map<String, Nbt> map, long[] longs) {
            this.type = type; this.num = num; this.str = str; this.items = items; this.map = map; this.longs = longs;
        }

        public Nbt get(String k) { return map == null ? null : map.get(k); }
        public String asString(String def) { return type == STRING && str != null ? str : def; }
        public long asLong(long def) { return (type >= BYTE && type <= DOUBLE) ? num : def; }

        public static Nbt parse(byte[] data) throws Exception {
            R r = new R(data);
            if (r.u8() != COMPOUND) throw new Exception("NBT 根不是 COMPOUND");
            r.str();
            return r.payload(COMPOUND);
        }

        static final class R {
            final byte[] d;
            int p;
            R(byte[] d) { this.d = d; }
            int u8() { return p < d.length ? (d[p++] & 255) : 0; }
            String str() {
                if (p + 2 > d.length) return "";
                int len = ((d[p] & 255) << 8) | (d[p + 1] & 255); p += 2;
                if (p + len > d.length) { p = d.length; return ""; }
                String s = new String(d, p, len, java.nio.charset.StandardCharsets.UTF_8); p += len; return s;
            }
            long long8() { long v = 0; for (int i = 0; i < 8; i++) v = (v << 8) | u8(); return v; }
            int int4() { int v = 0; for (int i = 0; i < 4; i++) v = (v << 8) | u8(); return v; }
            Nbt payload(int t) {
                switch (t) {
                    case BYTE: return new Nbt(BYTE, u8(), null, null, null, null);
                    case SHORT: { long v = 0; for (int i = 0; i < 2; i++) v = (v << 8) | u8(); return new Nbt(SHORT, (short) v, null, null, null, null); }
                    case INT: return new Nbt(INT, int4(), null, null, null, null);
                    case LONG: return new Nbt(LONG, long8(), null, null, null, null);
                    case FLOAT: return new Nbt(FLOAT, int4(), null, null, null, null);
                    case DOUBLE: return new Nbt(DOUBLE, long8(), null, null, null, null);
                    case BYTEARRAY: { int n = int4(); p += n; return new Nbt(BYTEARRAY, n, null, null, null, null); }
                    case STRING: return new Nbt(STRING, 0, str(), null, null, null);
                    case LIST: { int et = u8(); int n = int4(); List<Nbt> it = new ArrayList<>(Math.max(0, Math.min(n, 8192)));
                        for (int i = 0; i < n; i++) it.add(payload(et)); return new Nbt(LIST, n, null, it, null, null); }
                    case COMPOUND: { Map<String, Nbt> m = new LinkedHashMap<>();
                        while (p < d.length) { int ct = u8(); if (ct == END) break; String cn = str(); m.put(cn, payload(ct)); }
                        return new Nbt(COMPOUND, 0, null, null, m, null); }
                    case INTARRAY: { int n = int4(); p += 4L * n; return new Nbt(INTARRAY, n, null, null, null, null); }
                    case LONGARRAY: { int n = int4(); long[] l = new long[Math.max(0, Math.min(n, (d.length - p) / 8))];
                        for (int i = 0; i < l.length; i++) l[i] = long8(); return new Nbt(LONGARRAY, l.length, null, null, null, l); }
                    default: throw new IllegalStateException("未知 NBT 类型 " + t);
                }
            }
        }
    }
}
