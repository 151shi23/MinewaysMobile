package com.mineways;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewAssetLoader;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 粒子编辑器（Snowstorm 离线版 · 汉化）。
 *
 * <p>内置 JannisX11 的 Snowstorm（Minecraft 基岩版粒子编辑器，GPL-3.0），
 * 资源镜像在 {@code assets/snowstorm/}，全离线运行、不联网。
 *
 * <p>三处适配（都写在注入脚本 {@code assets/snowstorm/hans.js} 与下面的 {@link Bridge} 里）：
 * <ol>
 *   <li><b>汉化</b>：Snowstorm 无 i18n，界面文案硬编码。注入脚本按
 *       {@code zh-dict.js} 的英→中对照只替换显示文本（文本节点与 title/placeholder 等属性），
 *       不改任何逻辑值，因此不影响导出内容与模拟结果；</li>
 *   <li><b>导出</b>：Snowstorm 用 Blob + {@code <a download>} 下载，Android WebView 存不了
 *       blob 链接。注入脚本拦截该路径，把内容交给 {@link Bridge#saveText}，
 *       由原生写入「下载/MinewaysMobile/粒子/」（.particle.json/.mcfunction 文本与截图 PNG 都支持）；</li>
 *   <li><b>导入</b>：保留 Snowstorm 自带的文件输入框，由 {@code onShowFileChooser}
 *       拉起系统文件选择器（可直接导入 .particle.json）。</li>
 * </ol>
 */
public class SnowstormActivity extends AppCompatActivity {

    private static final String TAG = "Snowstorm";

    /** 引擎页地址（WebViewAssetLoader 的虚拟 https 源，与站点同源，可离线运行）。 */
    private static final String PAGE_URL =
            "https://appassets.androidplatform.net/assets/snowstorm/index.html";

    /** 产物落盘目录（Download/MinewaysMobile/ 下）。 */
    private static final String SUBDIR = "粒子";

    private WebView webView;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private WebViewAssetLoader assetLoader;

    private ValueCallback<Uri[]> filePathCallback;

    private final ActivityResultLauncher<Intent> fileChooserLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (filePathCallback == null) return;
                Uri[] uris = null;
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    if (data.getClipData() != null) {
                        int n = data.getClipData().getItemCount();
                        uris = new Uri[n];
                        for (int i = 0; i < n; i++) {
                            uris[i] = data.getClipData().getItemAt(i).getUri();
                        }
                    } else if (data.getData() != null) {
                        uris = new Uri[]{data.getData()};
                    }
                }
                filePathCallback.onReceiveValue(uris);
                filePathCallback = null;
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_snowstorm);

        MaterialToolbar bar = findViewById(R.id.ss_toolbar);
        setSupportActionBar(bar);
        bar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        bar.setNavigationOnClickListener(v -> finish());

        webView = findViewById(R.id.ss_webview);
        progressBar = findViewById(R.id.ss_progress);
        tvStatus = findViewById(R.id.ss_status);

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        setupWebView();
        tvStatus.setText(R.string.ss_status_loading);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setLoadsImagesAutomatically(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);

        // 原生保存桥（导出接管的目标；名字不要改，hans.js 按此调用）
        webView.addJavascriptInterface(new Bridge(), "SSAndroid");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                WebResourceResponse response = assetLoader.shouldInterceptRequest(request.getUrl());
                if (response != null) {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Access-Control-Allow-Origin", "*");
                    response.setResponseHeaders(headers);
                }
                return response;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                tvStatus.setText(R.string.ss_status_ready);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage m) {
                if (m != null) {
                    Log.i(TAG, "console: " + m.message());
                }
                return true;
            }

            /** 导入：Snowstorm 的文件输入框 -> 系统文件选择器。 */
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                Intent chooser = null;
                try {
                    chooser = params.createIntent();
                } catch (Throwable ignored) {
                }
                if (chooser == null) {
                    chooser = new Intent(Intent.ACTION_GET_CONTENT);
                    chooser.setType("*/*");
                    chooser.addCategory(Intent.CATEGORY_OPENABLE);
                }
                try {
                    fileChooserLauncher.launch(Intent.createChooser(chooser, getString(R.string.ss_pick_file)));
                } catch (Throwable t) {
                    filePathCallback.onReceiveValue(null);
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        // 兜底：正常导出已被 hans.js 接管；若有直链下载逃到原生，给一句可读提示
        webView.setDownloadListener((url, ua, cd, mime, len) ->
                Toast.makeText(this, R.string.ss_download_unsupported, Toast.LENGTH_LONG).show());

        // 建模/调参时保持屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        webView.loadUrl(PAGE_URL);
    }

    /**
     * 导出接管的落点。
     * <p>内容分两类：
     * <ul>
     *   <li>普通文本（.particle.json / .mcfunction）—— 按 UTF-8 写盘；</li>
     *   <li>data URL（截图等二进制，形如 {@code data:image/png;base64,...}）—— 解码后写盘。</li>
     * </ul>
     * 均写入「下载/MinewaysMobile/粒子/」，Android 10+ 走 MediaStore。
     */
    private final class Bridge {
        @JavascriptInterface
        public void saveText(final String name, final String content) {
            runOnUiThread(() -> {
                try {
                    String fileName = sanitize(name);
                    byte[] data;
                    String mime;
                    if (content != null && content.startsWith("data:")) {
                        int comma = content.indexOf(',');
                        String header = content.substring(5, comma);
                        boolean b64 = header.endsWith(";base64");
                        String m = header.split(";")[0];
                        mime = m.isEmpty() ? "application/octet-stream" : m;
                        String payload = content.substring(comma + 1);
                        data = b64 ? Base64.decode(payload, Base64.DEFAULT)
                                : Uri.decode(payload).getBytes(StandardCharsets.UTF_8);
                        if (fileName.length() == 0) fileName = "snowstorm_export";
                    } else {
                        mime = "application/json";
                        data = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
                    }
                    String where = writeFile(fileName, mime, data);
                    tvStatus.setText(getString(R.string.ss_saved, where));
                    Toast.makeText(SnowstormActivity.this,
                            getString(R.string.ss_saved, where), Toast.LENGTH_LONG).show();
                } catch (Throwable t) {
                    Log.w(TAG, "save failed", t);
                    tvStatus.setText(getString(R.string.ss_save_failed, String.valueOf(t.getMessage())));
                    Toast.makeText(SnowstormActivity.this,
                            getString(R.string.ss_save_failed, String.valueOf(t.getMessage())),
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    /** 写盘；返回落点描述（公共下载目录或应用私有目录）。 */
    private String writeFile(String fileName, String mime, byte[] data) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            cv.put(MediaStore.Downloads.MIME_TYPE, mime);
            cv.put(MediaStore.Downloads.RELATIVE_PATH, "Download/MinewaysMobile/" + SUBDIR);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) {
                throw new Exception("MediaStore 创建失败");
            }
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) {
                    throw new Exception("MediaStore 打开失败");
                }
                os.write(data);
            }
            return "下载/MinewaysMobile/" + SUBDIR + "/" + fileName;
        }
        File dir = getExternalFilesDir(SUBDIR);
        if (dir == null) {
            dir = getFilesDir();
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new Exception("目录创建失败");
        }
        File out = new File(dir, fileName);
        try (FileOutputStream fo = new FileOutputStream(out)) {
            fo.write(data);
        }
        return out.getAbsolutePath();
    }

    /** 文件名净化：只保留可用字符，避免路径穿越与非法名。 */
    private static String sanitize(String name) {
        String s = name == null ? "" : name.trim();
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (slash >= 0) {
            s = s.substring(slash + 1);
        }
        s = s.replaceAll("[^0-9A-Za-z_\\u4e00-\\u9fa5\\.\\-]", "_");
        return s;
    }

    @Override
    public void onBackPressed() {
        // 网页内有面板打开时优先交给页面处理（Snowstorm 自己处理返回更自然）
        try {
            webView.evaluateJavascript(
                    "(function(){try{var e=new KeyboardEvent('keydown',{key:'Escape',keyCode:27,bubbles:true});"
                            + "document.dispatchEvent(e);return 'ok';}catch(err){return 'no';}})()", null);
        } catch (Throwable ignored) {
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            webView.removeJavascriptInterface("SSAndroid");
            webView.loadUrl("about:blank");
            webView.destroy();
        } catch (Throwable t) {
            Log.w(TAG, "destroy webview failed", t);
        }
    }
}
