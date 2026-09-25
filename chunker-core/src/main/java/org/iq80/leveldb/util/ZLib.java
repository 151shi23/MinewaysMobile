package org.iq80.leveldb.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Android 兼容版 ZLib：顶替 leveldb 1.1.0 兼容 jar 里的同名类（jar 内那个 class 已删除）。
 *
 * <p>为什么必须顶替：原实现用的是 Java 11 才有的重载
 * {@code Inflater.setInput(ByteBuffer)} 与 {@code Inflater.inflate(ByteBuffer)}，
 * Android 的 libcore 直到 API 34 才提供这两个方法，于是 API 33 及以下的机器在读取
 * 基岩版存档（LevelDB 的 zlib 压缩块，读取路径 {@code org.iq80.leveldb.table.Table}）
 * 时会抛：
 * <pre>
 * java.lang.NoSuchMethodError: No virtual method setInput(Ljava/nio/ByteBuffer;)V
 *   in class Ljava/util/zip/Inflater; or its super classes
 *   (declaration of 'java.util.zip.Inflater' appears in /apex/com.android.art/javalib/core-oj.jar)
 * </pre>
 *
 * <p>本实现只用自 API 1 起就存在的 {@code byte[]} 形式，语义与 JDK 版保持一致：
 * <ul>
 *   <li>{@code setInput}：把 buffer 从 position 到 limit 的内容全部交给 inflater，并把 position 推到 limit；</li>
 *   <li>{@code inflate}：把解压结果写进 buffer 并推进 position，返回本次写入字节数。</li>
 * </ul>
 *
 * <p>另外：当调用方传入的 {@code useGzipIfAvailable} 与数据实际封装不一致时，
 * 会用另一种封装重试一次（zlib 有固定头，误判必然报 DataFormatException，不会静默出错）。
 */
public final class ZLib {

    /** nowrap=false：接受带 zlib/gzip 头的流（Bedrock/LevelDB 的常规情况）。 */
    private static final ThreadLocal<Inflater> INFLATER_ZLIB =
            ThreadLocal.withInitial(() -> new Inflater(false));
    /** nowrap=true：接受无头的 raw deflate 流。 */
    private static final ThreadLocal<Inflater> INFLATER_RAW =
            ThreadLocal.withInitial(() -> new Inflater(true));

    private static final ThreadLocal<Deflater> DEFLATER_ZLIB =
            ThreadLocal.withInitial(() -> new Deflater(Deflater.DEFAULT_COMPRESSION, false));
    private static final ThreadLocal<Deflater> DEFLATER_RAW =
            ThreadLocal.withInitial(() -> new Deflater(Deflater.DEFAULT_COMPRESSION, true));

    private ZLib() {
    }

    /**
     * 解压 {@code input}（等价于原实现），返回一个已 flip、可直接读取的结果 buffer。
     */
    public static ByteBuffer uncompress(ByteBuffer input, boolean useGzipIfAvailable) throws IOException {
        int remaining = input.remaining();
        byte[] data = new byte[remaining];
        input.get(data);   // 与 JDK 版"输入被完全消费"的最终状态一致
        try {
            return inflate(data, !useGzipIfAvailable);
        } catch (DataFormatException first) {
            try {
                return inflate(data, useGzipIfAvailable);
            } catch (DataFormatException second) {
                throw new IOException("zlib 解压失败：zlib 头与 raw deflate 两种方式都试过", second);
            }
        }
    }

    /**
     * 压缩 {@code input[inputOffset, inputOffset+inputLength)} 到 {@code output}，
     * 返回写入的字节数（等价于原实现）。
     */
    public static int compress(byte[] input, int inputOffset, int inputLength,
                               byte[] output, int outputOffset, boolean useGzipIfAvailable) throws IOException {
        Deflater deflater = (useGzipIfAvailable ? DEFLATER_ZLIB : DEFLATER_RAW).get();
        try {
            deflater.reset();
            deflater.setInput(input, inputOffset, inputLength);
            deflater.finish();
            int written = 0;
            while (!deflater.finished()) {
                int n = deflater.deflate(output, outputOffset + written,
                        output.length - outputOffset - written);
                if (n == 0 && !deflater.finished()) {
                    throw new IOException("压缩输出缓冲区不足");
                }
                written += n;
            }
            return written;
        } finally {
            deflater.reset();
        }
    }

    /** 单次解压尝试；{@code raw=true} 时不认 zlib/gzip 头。 */
    private static ByteBuffer inflate(byte[] data, boolean raw) throws DataFormatException, IOException {
        Inflater inflater = (raw ? INFLATER_RAW : INFLATER_ZLIB).get();
        try {
            inflater.reset();
            inflater.setInput(data, 0, data.length);

            ByteBuffer out = ByteBuffer.allocate(Math.max(data.length * 2, 256));
            while (!inflater.finished()) {
                int n;
                if (out.hasArray()) {
                    int offset = out.arrayOffset() + out.position();
                    n = inflater.inflate(out.array(), offset, out.remaining());
                    if (n > 0) {
                        out.position(out.position() + n);
                    }
                } else {
                    byte[] temp = new byte[out.remaining()];
                    n = inflater.inflate(temp, 0, temp.length);
                    if (n > 0) {
                        out.put(temp, 0, n);
                    }
                }
                if (n > 0) {
                    continue;
                }
                if (inflater.finished()) {
                    break;
                }
                if (inflater.needsInput()) {
                    throw new IOException("压缩数据不完整（提前结束）");
                }
                if (inflater.needsDictionary()) {
                    throw new IOException("该数据需要预设字典，Android 端不支持");
                }
                // n == 0 且仍需要输出空间 → 输出缓冲区翻倍（保留已解压内容）
                ByteBuffer bigger = ByteBuffer.allocate(out.capacity() * 2);
                out.flip();
                bigger.put(out);
                out = bigger;
            }
            out.flip();
            return out;
        } finally {
            inflater.reset();
        }
    }
}
