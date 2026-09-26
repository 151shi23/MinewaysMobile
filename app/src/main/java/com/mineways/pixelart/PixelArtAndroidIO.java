// ============================================================================
// PixelArtAndroidIO.java — Android 侧 Bitmap 边界（IO 壳层，纯算法在 PixelArtConverter）
// 只做三件事：Bitmap 解码 / Bitmap ↔ int[][] 像素矩阵互转 / 产物落盘。
// 核心算法零 Android 依赖，这一层随时可替换（测试时可喂 BufferedImage/JVM 矩阵）。
//
// 用法（Activity / Worker 内三行调用）：
//   int[][] px = PixelArtAndroidIO.bitmapToMatrix(bitmap);
//   PixelArtConverter.ConvertResult r = PixelArtConverter.convert(px, w, h, 10, 12, 1.0, "pixel_tool");
//   PixelArtAndroidIO.saveAll(getFilesDir(), "tool", r);
// ============================================================================
package com.mineways.pixelart;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class PixelArtAndroidIO {

    private PixelArtAndroidIO() {}

    /** 解码图片文件/流为 ARGB_8888（对应 Python Image.open().convert("RGBA")） */
    public static Bitmap decode(InputStream in) {
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap b = BitmapFactory.decodeStream(in, null, opt);
        if (b == null) throw new IllegalArgumentException("图片解码失败");
        // 兜底：部分低端机型/旧 API 可能返回 565 或硬件位图
        if (b.getConfig() != Bitmap.Config.ARGB_8888) {
            Bitmap copy = b.copy(Bitmap.Config.ARGB_8888, false);
            b.recycle();
            return copy;
        }
        return b;
    }

    /** Bitmap → px[y][x] ARGB 矩阵（喂给 PixelArtConverter.convert） */
    public static int[][] bitmapToMatrix(Bitmap bmp) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] buf = new int[w * h];
        bmp.getPixels(buf, 0, w, 0, 0, w, h); // 行优先，与 Python 双层循环顺序一致
        int[][] px = new int[h][w];
        for (int y = 0; y < h; y++)
            System.arraycopy(buf, y * w, px[y], 0, w);
        return px;
    }

    /** int[][] ARGB 矩阵 → Bitmap（调试用/预览用） */
    public static Bitmap matrixToBitmap(int[][] px) {
        int h = px.length, w = px[0].length;
        int[] buf = new int[w * h];
        for (int y = 0; y < h; y++)
            System.arraycopy(px[y], 0, buf, y * w, w);
        return Bitmap.createBitmap(buf, w, h, Bitmap.Config.ARGB_8888);
    }

    /** 落盘：dir/name.bbmodel + name.obj + name.mtl + name.png（贴图，OBJ 引用同名） */
    public static void saveAll(File dir, String name, PixelArtConverter.ConvertResult r) throws IOException {
        writeFile(new File(dir, name + ".bbmodel"), r.bbmodelJson.getBytes("UTF-8"));
        writeFile(new File(dir, name + ".obj"), r.obj.getBytes("UTF-8"));
        writeFile(new File(dir, name + ".mtl"), r.mtl.getBytes("UTF-8"));
        writeFile(new File(dir, name + ".png"), r.texturePng);
    }

    private static void writeFile(File f, byte[] data) throws IOException {
        FileOutputStream out = new FileOutputStream(f);
        try { out.write(data); } finally { out.close(); }
    }
}
