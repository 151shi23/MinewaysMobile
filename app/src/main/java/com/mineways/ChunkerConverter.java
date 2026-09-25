package com.mineways;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hivemc.chunker.cli.messenger.Messenger;
import com.hivemc.chunker.conversion.WorldConverter;
import com.hivemc.chunker.conversion.encoding.EncodingType;
import com.hivemc.chunker.conversion.encoding.base.reader.LevelReader;
import com.hivemc.chunker.conversion.encoding.base.writer.LevelWriter;
import com.hivemc.chunker.scheduling.task.TrackedTask;
import com.hivemc.chunker.util.ChunkerResources;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/**
 * Chunker 世界转换驱动（Android 专用，不改 Chunker 算法）。
 * <p/>
 * 复刻 Chunker CLI 的 encode 流程：
 * <ol>
 *   <li>用 {@link EncodingType#findReader} 探测输入版本；</li>
 *   <li>用 {@link Messenger#findWriter} 按用户选择的输出格式构造写入器；</li>
 *   <li>用 {@link WorldConverter#convert} 执行转换并在后台轮询进度。</li>
 * </ol>
 * 全程纯 Java 本地计算，无需配置。资源（映射 zip/json）在首次调用时由 assets 解压到
 * 应用私有目录，注入 {@link ChunkerResources}。
 */
public final class ChunkerConverter {

    /** assets 内 Chunker 资源子目录。 */
    private static final String ASSET_ROOT = "convert/java";

    private ChunkerConverter() {
    }

    /**
     * 把内置映射资源从 assets 解压到应用私有目录，并注入 Chunker 资源加载器。
     * 幂等：目录已存在则直接跳过。建议在转换前调用。
     *
     * @param context 应用上下文。
     */
    public static synchronized void ensureResources(Context context) throws IOException {
        File root = new File(context.getFilesDir(), "chunker_res");
        File javaDir = new File(root, "java");
        String[] names = {"CavesAndCliffsHeightPack.zip", "default_font_width_codepoints.json"};

        if (javaDir.isDirectory()) {
            boolean missing = false;
            for (String name : names) {
                if (!new File(javaDir, name).isFile()) {
                    missing = true;
                    break;
                }
            }
            if (!missing) {
                ChunkerResources.setResourceDirectory(root);
                return;
            }
        }

        //noinspection ResultOfMethodCallBeingIgnored
        javaDir.mkdirs();
        for (String name : names) {
            copyAsset(context, ASSET_ROOT + "/" + name, new File(javaDir, name));
        }
        ChunkerResources.setResourceDirectory(root);
    }

    private static void copyAsset(Context context, String asset, File target) throws IOException {
        try (InputStream in = context.getAssets().open(asset);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        }
    }

    /** 输出格式条目，携带 Chunker 用的 writer id（如 BEDROCK_1_20_80）与展示文案。 */
    public static final class OutputFormat {
        /** 设备内使用的 id（传给 Messenger.findWriter 解析），如 BEDROCK_1_20_80。 */
        public final String id;
        /** 目标格式名，如 Bedrock Edition / Java Edition。 */
        public final String label;
        /** 具体目标版本，如 1.20.80；为 null 表示该格式不区分版本。 */
        public final String version;

        public OutputFormat(String id, String label, String version) {
            this.id = id;
            this.label = label;
            this.version = version;
        }

        @Override
        public String toString() {
            // 下拉列表展示：带版本（如 "Bedrock Edition 1.20.80"）；无版本则只显示格式名
            return (version == null || version.isEmpty())
                    ? label
                    : label + " " + version;
        }
    }

    /**
     * 枚举所有可选输出格式（排除内部格式）。与 ChunkerWeb 的 writer 列表一致。
     *
     * @return 输出格式列表（已按 UI 顺序排序）。
     */
    public static List<OutputFormat> listOutputFormats() {
        // 复用 Messenger 的 writer 编码逻辑（含 Bedrock 的 R 前缀与 version），见 getWriters/toEncodedObject。
        JsonArray writers = Messenger.getWriters();
        List<OutputFormat> out = new ArrayList<>(writers.size());
        for (JsonElement element : writers) {
            JsonObject json = element.getAsJsonObject();
            String version = json.has("version") ? json.get("version").getAsString() : null;
            out.add(new OutputFormat(
                    json.get("id").getAsString(),
                    json.get("label").getAsString(),
                    version));
        }
        return out;
    }

    /** 转换回调。 */
    public interface ProgressListener {
        /** 进度百分比（0~1）。 */
        void onProgress(double progress);

        /** 成功。message 为友好提示。 */
        void onSuccess(String message);

        /** 失败。message 为友好错误。 */
        void onFailure(String message);

        /** 取消（用户主动停止）。 */
        void onCancelled();
    }

    /**
     * 在一个后台线程中执行世界转换。
     *
     * @param context   应用上下文（用于解压内置资源）。
     * @param inputDir  已复制到应用私有目录的输入存档文件夹（含 level.dat）。
     * @param outputDir 输出目录（自动创建/清空）。
     * @param formatId  目标格式 id（见 {@link OutputFormat#id}）。
     * @param listener  进度/结果回调（后台线程调用，请自行切回 UI 线程）。
     * @param cancelled 可选的取消标志，置 true 时尽快中断轮询并取消转换。
     */
    public static void convert(Context context, File inputDir, File outputDir,
                               String formatId, ProgressListener listener, BooleanSupplier cancelled) {
        Thread thread = new Thread(() -> runConversion(context, inputDir, outputDir, formatId, listener, cancelled),
                "chunker-convert");
        thread.start();
    }

    private static void runConversion(Context context, File inputDir, File outputDir,
                                      String formatId, ProgressListener listener,
                                      BooleanSupplier cancelled) {
        WorldConverter worldConverter = null;
        CompletableFuture<Void> future = null;
        try {
            ensureResources(context);

            // 输出目录准备
            if (outputDir.isDirectory()) {
                deleteRecursive(outputDir);
            }
            //noinspection ResultOfMethodCallBeingIgnored
            outputDir.mkdirs();

            worldConverter = new WorldConverter(UUID.randomUUID());
            worldConverter.setLevelDBCompaction(false); // 手机端关闭 LevelDB 压缩，省时省电

            // 1) 探测输入
            Optional<? extends LevelReader> reader = EncodingType.findReader(inputDir, worldConverter);
            if (reader.isEmpty()) {
                listener.onFailure("无法识别输入存档格式（不是有效的 Java / 基岩版存档）。");
                return;
            }

            // 2) 按用户选择构造写入器
            Optional<? extends LevelWriter> writer = Messenger.findWriter(formatId, worldConverter, outputDir);
            if (writer.isEmpty()) {
                listener.onFailure("目标格式不受支持：" + formatId);
                return;
            }

            // 3) 执行转换
            TrackedTask<Void> task = worldConverter.convert(reader.get(), writer.get());
            future = task.future();

            Double last = null;
            boolean cancelledFlag = false;
            while (true) {
                if (cancelled != null && cancelled.getAsBoolean()) {
                    cancelledFlag = true;
                    break;
                }
                if (future.isDone()) {
                    break;
                }
                double progress = task.getProgress();
                if (last == null || progress > last + 0.01) {
                    last = progress;
                    listener.onProgress(progress);
                }
                Thread.sleep(200);
            }

            if (cancelledFlag) {
                worldConverter.cancel(null);
                listener.onCancelled();
                return;
            }

            // 4) 收尾：等待任务真正结束并检查异常
            try {
                future.join();
            } catch (CompletionException ex) {
                throw (Throwable) (ex.getCause() != null ? ex.getCause() : ex);
            }

            if (worldConverter.isCancelled()) {
                listener.onCancelled();
            } else {
                listener.onSuccess("转换完成！输出目录：\n" + outputDir.getAbsolutePath());
            }
        } catch (Throwable t) {
            if (cancelled != null && cancelled.getAsBoolean()) {
                listener.onCancelled();
            } else {
                listener.onFailure("转换失败：" + t.getMessage());
            }
        }
    }

    private static void deleteRecursive(File f) {
        if (f == null) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) deleteRecursive(k);
            }
        }
        //noinspection ResultOfMethodCallBeingIgnored
        f.delete();
    }
}