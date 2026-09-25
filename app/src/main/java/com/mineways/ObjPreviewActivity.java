package com.mineways;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 独立 3D 预览界面。
 *
 * <p>渲染用开源库 <b>three.js (MIT)</b> + OBJLoader / MTLLoader / OrbitControls，
 * 全部文件内置在 {@code assets/objviewer/lib}，离线可用、不依赖网络。
 *
 * <p>三种进模型的方式：
 * <ol>
 *   <li>{@link #EXTRA_MODEL_DIR} + {@link #EXTRA_OBJ_NAME}：直接指向导出目录（最常用）；</li>
 *   <li>{@link #EXTRA_DOC_URI}：外部（SAF）选中的 .obj，先复制到缓存目录再预览；</li>
 *   <li>界面里的「选择 .obj 文件」按钮：任何时候都能换个模型看。</li>
 * </ol>
 *
 * <p>文件如何送到网页：WebView 不允许网页读本地文件，因此用
 * {@link WebViewClient#shouldInterceptRequest} 把 {@code https://appassets.androidplatform.net/...}
 * 映射为：{@code /objviewer/*} → assets 查看器；{@code /model/*} → 模型目录（只读 + 越权防护）。
 * 两者同源，页面里的 fetch 才能取到 OBJ / MTL / PNG。
 */
public class ObjPreviewActivity extends AppCompatActivity {

    public static final String EXTRA_MODEL_DIR = "model_dir";
    public static final String EXTRA_OBJ_NAME = "obj_name";
    /** 通过 SAF 选中的 .obj（content:// 或 file://）。 */
    public static final String EXTRA_DOC_URI = "doc_uri";
    /** 来源说明，仅用于界面显示（如"最近导出"/"ZIP 解包"）。 */
    public static final String EXTRA_LABEL = "label";

    /** 与查看器内约定的虚拟域名。 */
    private static final String HOST = "https://appassets.androidplatform.net";

    /** 导入外部分模型时的上限，避免把巨大目录拷进来。 */
    private static final int IMPORT_MAX_FILES = 600;
    private static final long IMPORT_MAX_BYTES = 200L * 1024 * 1024;

    private static final int REQ_PICK = 4201;
    private static final String TAG = "ObjPreview";

    private WebView webView;
    private ProgressBar progress;
    private TextView status;
    private MaterialToolbar toolbar;

    private File modelDir;
    private String objName;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_obj_preview);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        toolbar = findViewById(R.id.preview_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());

        progress = findViewById(R.id.preview_progress);
        status = findViewById(R.id.preview_status);

        findViewById(R.id.btn_preview_pick).setOnClickListener(v -> pickObjFile());
        findViewById(R.id.btn_preview_reload).setOnClickListener(v -> reload());
        findViewById(R.id.btn_preview_close).setOnClickListener(v -> finish());
        findViewById(R.id.btn_preview_diag).setOnClickListener(v -> showDiagnostics());

        webView = findViewById(R.id.preview_webview);
        setupWebView();

        if (!resolveSource(getIntent())) return;   // 没有可用来源时已提示并结束
        loadViewer();
    }

    /** 解析模型来源：外部选中的文件优先，其次导出目录。 */
    private boolean resolveSource(Intent intent) {
        if (intent == null) {
            toast(getString(R.string.preview_no_model));
            finish();
            return false;
        }
        String doc = intent.getStringExtra(EXTRA_DOC_URI);
        if (doc != null && !doc.isEmpty()) {
            try {
                File imported = importDocument(Uri.parse(doc));
                if (imported != null) {
                    modelDir = imported.getParentFile();
                    objName = imported.getName();
                    setTitleText(getString(R.string.preview_title) + " · " + objName,
                            intent.getStringExtra(EXTRA_LABEL));
                    return true;
                }
            } catch (Throwable t) {
                Log.w(TAG, "导入外部文件失败", t);
            }
        }
        modelDir = new File(intent.getStringExtra(EXTRA_MODEL_DIR) == null
                ? "" : intent.getStringExtra(EXTRA_MODEL_DIR));
        objName = intent.getStringExtra(EXTRA_OBJ_NAME);
        if (objName == null || objName.isEmpty()) objName = "mineways_export.obj";
        File obj = new File(modelDir, objName);
        if (!obj.isFile()) {
            toast(getString(R.string.preview_no_model) + "\n" + modelDir.getAbsolutePath());
            finish();
            return false;
        }
        setTitleText(getString(R.string.preview_title) + " · " + objName, intent.getStringExtra(EXTRA_LABEL));
        return true;
    }

    private void setTitleText(String title, String subtitle) {
        toolbar.setTitle(title);
        if (subtitle != null && !subtitle.isEmpty()) toolbar.setSubtitle(subtitle);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        webView.setBackgroundColor(Color.rgb(21, 23, 27));
        webView.setWebViewClient(new LocalModelClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                view.setVisibility(View.VISIBLE);
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                if (newProgress >= 100 && status.getText().length() > 0
                        && !status.getText().toString().startsWith("[错误]")) {
                    status.setVisibility(View.GONE);
                }
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage m) {
                String line = m.message() + " @" + m.sourceId() + ":" + m.lineNumber();
                Log.i(TAG, "console: " + line);
                if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    // 网页内部把错误显示在红框里；这里同步到底部状态条，双保险
                    status.setText("[错误] " + m.message());
                    status.setVisibility(View.VISIBLE);
                }
                return true;
            }
        });
    }

    private void loadViewer() {
        try {
            String url = HOST + "/objviewer/index.html?obj=" + Uri.encode(objName);
            Log.i(TAG, "加载查看器: " + url + "  modelDir=" + modelDir);
            status.setText(getString(R.string.preview_loading) + "  " + objName
                    + "\n" + modelDir.getAbsolutePath());
            status.setVisibility(View.VISIBLE);
            webView.loadUrl(url);
        } catch (Throwable t) {
            toast("无法打开预览：" + t);
            finish();
        }
    }

    private void reload() {
        if (objName != null) loadViewer();
    }

    /** 任何时候都能另选一个 .obj（不必经过导出流程）。 */
    private void pickObjFile() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_PICK);
        } catch (Throwable t) {
            toast("打不开文件选择器：" + t);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK || resultCode != Activity.RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            File f = importDocument(uri);
            if (f == null) {
                toast("读取失败：不是有效的 .obj 文件");
                return;
            }
            modelDir = f.getParentFile();
            objName = f.getName();
            setTitleText(getString(R.string.preview_title) + " · " + objName, "手动选择");
            loadViewer();
        } catch (Throwable t) {
            toast("读取失败：" + t);
        }
    }

    /**
     * 把外部选中的 .obj 复制到缓存目录，并**尽力**把同目录的 .mtl / 贴图一起带过来
     * （保持相对路径，例如 {@code tex/*.png}，这样查看器的自动贴图仍可用）。
     * 受系统存储限制拿不到同目录文件时，模型照样能看，只是没有贴图。
     */
    private File importDocument(Uri uri) throws IOException {
        String name = displayName(uri);
        if (name == null || name.trim().isEmpty()) name = "picked_model.obj";
        if (!name.toLowerCase(Locale.US).endsWith(".obj")) {
            String lower = name.toLowerCase(Locale.US);
            if (lower.endsWith(".zip")) throw new IOException("这是 ZIP，请用「预览 ZIP 压缩包」入口");
            throw new IOException("请选择 .obj 文件（当前：" + name + "）");
        }
        File dir = new File(getCacheDir(), "preview_import");
        deleteRecursively(dir);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("无法创建缓存目录");
        File out = new File(dir, name);
        copyStream(openIn(uri), new FileOutputStream(out));

        // 尽力带回同目录的材质与贴图（外部存储可直读时；受限时静默跳过）
        try {
            File srcDir = guessSourceDir(uri);
            if (srcDir != null && srcDir.isDirectory()) {
                copySiblings(srcDir, dir, 0, new long[]{0}, new int[]{0});
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private InputStream openIn(Uri uri) throws IOException {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("无法读取该文件");
        return in;
    }

    /** 从 content:// 的 documentId 反推真实目录（可用时才有值）。 */
    private File guessSourceDir(Uri uri) {
        try {
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                File f = new File(uri.getPath() == null ? "" : uri.getPath());
                return f.getParentFile();
            }
            String docId = DocumentsContract.getDocumentId(uri);
            String[] parts = docId.split(":");
            if (parts.length == 2 && "primary".equalsIgnoreCase(parts[0])) {
                File f = new File(Environment.getExternalStorageDirectory(), parts[1]);
                return f.getParentFile();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 递归复制贴图/材质（跳过 .obj 与报错日志），有数量与体积上限。 */
    private void copySiblings(File src, File dst, int depth, long[] bytes, int[] files) {
        if (depth > 3 || files[0] >= IMPORT_MAX_FILES || bytes[0] >= IMPORT_MAX_BYTES) return;
        File[] kids = src.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (files[0] >= IMPORT_MAX_FILES || bytes[0] >= IMPORT_MAX_BYTES) return;
            String ln = k.getName().toLowerCase(Locale.US);
            if (k.isDirectory()) {
                if ("导出报错".equals(k.getName()) || ".thumbnails".equals(k.getName())) continue;
                File sub = new File(dst, k.getName());
                if (!sub.exists() && !sub.mkdirs()) continue;
                copySiblings(k, sub, depth + 1, bytes, files);
                continue;
            }
            if (!(ln.endsWith(".mtl") || ln.endsWith(".png") || ln.endsWith(".jpg")
                    || ln.endsWith(".jpeg"))) {
                continue;
            }
            try {
                File out = new File(dst, k.getName());
                copyStream(new FileInputStream(k), new FileOutputStream(out));
                bytes[0] += k.length();
                files[0]++;
            } catch (Throwable ignored) {
                // 单个文件失败不影响整体
            }
        }
    }

    private String displayName(Uri uri) {
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) c.close();
        }
        String seg = uri.getLastPathSegment();
        return seg == null ? null : new File(seg).getName();
    }

    /** 把页面里的状态（含红框错误）取出来，便于反馈问题时一眼定位。 */
    private void showDiagnostics() {
        try {
            webView.evaluateJavascript(
                    "(window.__viewerState ? window.__viewerState() : '(页面状态不可用)')",
                    value -> {
                        String text = value == null ? "" : value;
                        text = text.replace("\\n", "\n").replace("\\\"", "\"")
                                .replace("\\u003C", "<").replace("\\u003E", ">")
                                .replace("\\u0027", "'").replace("\\u0026", "&");
                        if (text.startsWith("\"") && text.endsWith("\"") && text.length() > 1) {
                            text = text.substring(1, text.length() - 1);
                        }
                        String body = "模型：" + objName + "\n目录：" + modelDir
                                + "\n\n—— 查看器状态 ——\n" + text;
                        final String toCopy = body;
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("预览诊断信息")
                                .setMessage(body)
                                .setPositiveButton("复制", (d, w) -> {
                                    android.content.ClipboardManager cm =
                                            (android.content.ClipboardManager)
                                                    getSystemService(CLIPBOARD_SERVICE);
                                    if (cm != null) cm.setPrimaryClip(
                                            android.content.ClipData.newPlainText("preview", toCopy));
                                    toast("已复制，粘贴即可反馈");
                                })
                                .setNegativeButton("关闭", null)
                                .show();
                    });
        } catch (Throwable t) {
            toast("诊断失败：" + t);
        }
    }

    @Override
    protected void onDestroy() {
        try {
            if (webView != null) {
                webView.loadUrl("about:blank");
                webView.destroy();
            }
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    // ---------------- 本地文件服务（assets 查看器 + 模型目录） ----------------

    private final class LocalModelClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            try {
                String url = request.getUrl().toString();
                if (!url.startsWith(HOST)) return null;
                String path = url.substring(HOST.length());
                int q = path.indexOf('?');
                if (q >= 0) path = path.substring(0, q);
                if (path.startsWith("/objviewer/")) {
                    String asset = path.substring("/objviewer/".length());
                    InputStream in = getAssets().open("objviewer/" + asset);
                    return okResponse(mimeOf(asset), in);
                }
                if (path.startsWith("/model/")) {
                    String rel = decode(path.substring("/model/".length()));
                    File f = resolveInside(modelDir, rel);
                    if (f == null || !f.isFile()) {
                        Log.w(TAG, "404 " + rel + "（目录：" + modelDir + "）");
                        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found",
                                new HashMap<String, String>(), null);
                    }
                    return okResponse(mimeOf(rel), new FileInputStream(f));
                }
            } catch (Throwable t) {
                Log.w(TAG, "拦截请求失败", t);
            }
            return null;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return true;   // 预览页里不做外链跳转
        }
    }

    private static WebResourceResponse okResponse(String mime, InputStream in) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", "no-store");
        headers.put("Access-Control-Allow-Origin", "*");
        return new WebResourceResponse(mime, "utf-8", 200, "OK", headers, in);
    }

    private static String mimeOf(String name) {
        String n = name.toLowerCase(Locale.US);
        if (n.endsWith(".html")) return "text/html";
        if (n.endsWith(".js")) return "application/javascript";
        if (n.endsWith(".css")) return "text/css";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".obj")) return "text/plain";
        if (n.endsWith(".mtl")) return "text/plain";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private static String decode(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Throwable t) {
            return s;
        }
    }

    /** 解析目录内文件，禁止 ../ 越权；返回 null 表示不合法。 */
    private static File resolveInside(File base, String rel) {
        try {
            if (rel == null || rel.isEmpty()) return null;
            File f = new File(base, rel);
            String basePath = base.getCanonicalPath();
            String filePath = f.getCanonicalPath();
            if (!filePath.startsWith(basePath + File.separator) && !filePath.equals(basePath)) return null;
            return f;
        } catch (IOException e) {
            return null;
        }
    }

    private static void copyStream(InputStream in, FileOutputStream out) throws IOException {
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            try { in.close(); } catch (Throwable ignored) { }
            try { out.flush(); out.close(); } catch (Throwable ignored) { }
        }
    }

    private static void deleteRecursively(File f) {
        try {
            if (f == null || !f.exists()) return;
            if (f.isDirectory()) {
                File[] kids = f.listFiles();
                if (kids != null) for (File k : kids) deleteRecursively(k);
            }
            f.delete();
        } catch (Throwable ignored) {
        }
    }
}
