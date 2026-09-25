package com.mineways;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * AI 动画助手（独立界面）：用户用自然语言描述动画逻辑（主打 logo 场景），
 * 由其自配的 OpenAI 兼容 API 生成 Blockbench/基岩版动画 JSON、Snowstorm 粒子 JSON 或 Molang 表达式。
 * 提示词由本 App 内置（含格式规范、真实组件清单、Molang 要点与 logo 领域映射）；
 * Key 只保存在本机偏好里。
 */
public class AiAnimActivity extends AppCompatActivity {

    /** 与 MainActivity 共用的偏好文件名（选项与 AI 配置同库存放）。 */
    private static final String OPT_PREFS = "export_options";
    private static final String PREF_ENDPOINT = "ai_endpoint";
    private static final String PREF_MODEL = "ai_model";
    private static final String PREF_KEY = "ai_key";

    private EditText etDesc, etLength, etName, etEndpoint, etModel, etKey, etResult;
    private TextView tvStatus;
    private RadioGroup rgTarget;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_anim);

        MaterialToolbar bar = findViewById(R.id.ai_toolbar);
        setSupportActionBar(bar);
        bar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        bar.setNavigationOnClickListener(v -> finish());

        rgTarget = findViewById(R.id.rg_ai_target);
        etDesc = findViewById(R.id.et_ai_desc);
        etLength = findViewById(R.id.et_ai_length);
        etName = findViewById(R.id.et_ai_name);
        etEndpoint = findViewById(R.id.et_ai_endpoint);
        etModel = findViewById(R.id.et_ai_model);
        etKey = findViewById(R.id.et_ai_key);
        etResult = findViewById(R.id.et_ai_result);
        tvStatus = findViewById(R.id.tv_ai_status);

        android.content.SharedPreferences sp = getSharedPreferences(OPT_PREFS, MODE_PRIVATE);
        etEndpoint.setText(sp.getString(PREF_ENDPOINT, "https://api.openai.com/v1/chat/completions"));
        etModel.setText(sp.getString(PREF_MODEL, "gpt-4o-mini"));
        etKey.setText(sp.getString(PREF_KEY, ""));
        etKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        findViewById(R.id.btn_ai_generate).setOnClickListener(v -> generate());
        findViewById(R.id.btn_ai_copy).setOnClickListener(v -> copyResult());
        findViewById(R.id.btn_ai_save).setOnClickListener(v -> saveResult());
    }

    /** 系统提示词：格式规范 + Molang 要点 + logo 领域映射 + 输出契约。 */
    private static String buildSystemPrompt(int target) {
        StringBuilder s = new StringBuilder();
        s.append("你是 Minecraft 基岩版(Bedrock)动画与粒子专家，把用户的自然语言描述转成可直接使用的产物。")
         .append("只输出产物本身，不要任何解释。\n\n")
         .append("【Molang 要点】\n")
         .append("查询: q.anim_time(动画已播秒数); 粒子: v.particle_age, v.particle_max_lifetime, v.particle_random_1..4(0..1随机)\n")
         .append("函数: math.sin, math.cos, math.clamp(v,min,max), math.lerp(a,b,t), math.random(a,b), math.pi\n")
         .append("变量: v.xxx，单条表达式内赋值用 (v.t = ...; 结果) 形式\n")
         .append("单位: 位置/尺寸=方块(米), 旋转=度, 时间=秒\n\n");
        if (target == 0) {
            s.append("【目标：Blockbench/基岩版动画 JSON（严格遵循）】\n")
             .append("{\"format_version\":\"1.8.0\",\"animations\":{\"animation.<短名>\":{")
             .append("\"loop\":true,\"animation_length\":3.0,\"bones\":{\"<骨骼名>\":{")
             .append("\"position\":{\"0.0\":[0,0,0],\"1.5\":[0,0.5,0],\"3.0\":[0,0,0]},")
             .append("\"rotation\":{\"0.0\":[0,0,0],\"3.0\":[0,360,0]},\"scale\":1.0}}}}}\n")
             .append("关键帧时间键是字符串数字(秒); rotation/position=[x,y,z]; scale 可为数字或数组\n")
             .append("关键帧值可直接写 Molang 字符串, 例 \"rotation\":{\"0.0\":[0,\"math.sin(q.anim_time * 60) * 10\",0]}\n")
             .append("loop: 循环动画必须 true; 单次动画用 \"hold_on_last_frame\"; 骨骼名用户没给就用 root\n")
             .append("用途: 文件放资源包 animations/, 或 Blockbench 里 Animation→Import Animations\n\n");
        } else if (target == 1) {
            s.append("【目标：Snowstorm/基岩版粒子 JSON（严格遵循）】\n")
             .append("{\"format_version\":\"1.10.0\",\"particle_effect\":{\"description\":{")
             .append("\"identifier\":\"mineways:<短名>\",\"basic_render_parameters\":{")
             .append("\"material\":\"particles_alpha\",\"texture\":\"textures/particle/particles\"}},")
             .append("\"components\":{\"minecraft:emitter_rate_steady\":{\"spawn_rate\":12,\"max_particles\":120},")
             .append("\"minecraft:emitter_lifetime_looping\":{\"active_time\":1},")
             .append("\"minecraft:emitter_shape_sphere\":{\"radius\":1.2,\"offset\":[0,1,0],\"direction\":\"outwards\"},")
             .append("\"minecraft:particle_lifetime_expression\":{\"max_lifetime\":1.5},")
             .append("\"minecraft:particle_initial_speed\":1.5,")
             .append("\"minecraft:particle_motion_dynamic\":{\"linear_acceleration\":[0,0.5,0],\"linear_drag_coefficient\":1.5},")
             .append("\"minecraft:particle_appearance_billboard\":{\"size\":[0.08,0.08],\"facing_camera_mode\":\"rotate_xyz\",")
             .append("\"uv\":{\"texture_width\":128,\"texture_height\":128,\"uv\":[0,64],\"uv_size\":[8,8]}},")
             .append("\"minecraft:particle_appearance_tinting\":{\"color\":{\"interpolant\":")
             .append("\"v.particle_age / v.particle_max_lifetime\",\"gradient\":{\"0.0\":\"#FFFFFF\",\"1.0\":\"#FFD24A\"}}}}}}\n")
             .append("只允许真实存在的组件, 不得编造: emitter_rate_steady/instant/manual; emitter_lifetime_looping/once; ")
             .append("emitter_shape_point/sphere/box/disc/entity_aabb(radius或dimensions, direction=outwards/inwards/[x,y,z]); ")
             .append("particle_lifetime_expression(max_lifetime); particle_initial_speed; particle_motion_dynamic")
             .append("(linear_acceleration, linear_drag_coefficient); particle_motion_collision; ")
             .append("particle_appearance_billboard(size是半径, facing_camera_mode=rotate_xyz/lookat_xyz/lookat_y/rotate_y/face_camera, ")
             .append("uv 可用 flipbook 动画帧); particle_appearance_tinting(color=\"#RRGGBB\"或[r,g,b,a]或{interpolant,gradient}); ")
             .append("particle_appearance_lighting\n")
             .append("原版图集: texture=textures/particle/particles, 宽高128, 常见精灵8x8(uv为像素坐标)\n")
             .append("性能: max_particles≤200; 用途: 存为 particles/<短名>.particle.json, /particle <identifier> ~ ~ ~ 播放\n\n");
        } else {
            s.append("【目标：Molang 表达式】输出一行可粘贴的表达式, 不是 JSON, 不要代码块不要解释\n")
             .append("动画上下文用 q.anim_time; 粒子上下文用 v.particle_age / v.particle_max_lifetime\n")
             .append("需要中间变量时用 (v.t = ...; 结果) 形式\n\n");
        }
        s.append("【logo 领域常用映射（优先使用，保持克制优雅）】\n")
         .append("上下浮动: position.y=\"math.sin(q.anim_time * 90) * 0.3\"(90≈4秒周期)\n")
         .append("缓慢自转: rotation.y 线性关键帧或 \"q.anim_time * 60\"\n")
         .append("呼吸缩放: scale=\"1 + 0.08 * math.sin(q.anim_time * 120)\"\n")
         .append("光环/闪光: emitter_shape_sphere + emitter_rate_steady + 渐变淡出\n")
         .append("弹性入场: scale 关键帧 0 → 1.08 → 1.0\n\n")
         .append("【输出契约】动画/粒子目标: 只输出一个 ```json 代码块, 块外无任何文字, JSON 内无注释无尾逗号; ")
         .append("Molang 目标: 只输出表达式本身; 用户没提的细节自行选择柔和、循环、低粒子数的默认值\n");
        return s.toString();
    }

    private void generate() {
        final String endpoint = etEndpoint.getText().toString().trim();
        final String model = etModel.getText().toString().trim();
        final String key = etKey.getText().toString().trim();
        final String desc = etDesc.getText().toString().trim();
        if (endpoint.isEmpty() || model.isEmpty() || key.isEmpty()) {
            toast(getString(R.string.ai_need_config));
            return;
        }
        if (desc.isEmpty()) {
            toast(getString(R.string.ai_need_desc));
            return;
        }
        int id = rgTarget.getCheckedRadioButtonId();
        final int target = id == R.id.rb_ai_particle ? 1 : (id == R.id.rb_ai_molang ? 2 : 0);
        final float len = clamp(parseFloat(etLength.getText().toString(), 3f), 0.5f, 30f);
        final String name = safeName(etName.getText().toString(), "logo");

        getSharedPreferences(OPT_PREFS, MODE_PRIVATE).edit()
                .putString(PREF_ENDPOINT, endpoint).putString(PREF_MODEL, model)
                .putString(PREF_KEY, key).apply();

        setBusy(true);
        tvStatus.setText(getString(R.string.ai_status_calling));
        etResult.setText("");

        final String sys = buildSystemPrompt(target);
        final String user = "目标短名：" + name + "\n循环时长（秒）：" + len
                + "\n应用场景：游戏 logo 展示（克制、平滑、循环）\n需求描述：\n" + desc;

        new Thread(() -> {
            String result = null, err = null;
            try {
                result = callChatApi(endpoint, key, model, sys, user);
            } catch (Throwable e) {
                err = e.getMessage() == null ? String.valueOf(e) : e.getMessage();
            }
            final String out = result, errMsg = err;
            runOnUiThread(() -> {
                setBusy(false);
                if (errMsg != null) {
                    tvStatus.setText(getString(R.string.ai_status_failed, errMsg));
                    return;
                }
                String content = out == null ? "" : out.trim();
                if (target != 2) {
                    String fenced = extractFenced(content);
                    if (!fenced.isEmpty()) content = fenced;
                    String pretty = tryPrettyJson(content);
                    if (pretty != null) content = pretty;
                    else tvStatus.setText(getString(R.string.ai_status_not_json));
                }
                etResult.setText(content);
                tvStatus.setText(getString(R.string.ai_status_done));
            });
        }, "ai-anim").start();
    }

    /** OpenAI 兼容 chat/completions 调用。 */
    private static String callChatApi(String endpoint, String key, String model,
                                      String system, String user) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("temperature", 0.4);
        JSONArray msgs = new JSONArray();
        JSONObject m1 = new JSONObject();
        m1.put("role", "system");
        m1.put("content", system);
        msgs.put(m1);
        JSONObject m2 = new JSONObject();
        m2.put("role", "user");
        m2.put("content", user);
        msgs.put(m2);
        body.put("messages", msgs);

        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(120000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Authorization", "Bearer " + key);
        try (OutputStream os = c.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String ln;
                while ((ln = r.readLine()) != null) sb.append(ln).append('\n');
            }
        }
        String text = sb.toString();
        if (code >= 400) throw new Exception("HTTP " + code + "：" + firstLine(text));
        JSONObject resp = new JSONObject(text);
        JSONArray choices = resp.optJSONArray("choices");
        if (choices == null || choices.length() == 0) throw new Exception("响应无 choices：" + firstLine(text));
        return choices.getJSONObject(0).getJSONObject("message").getString("content");
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        s = s.trim();
        int i = s.indexOf('\n');
        String r = i > 0 ? s.substring(0, i) : s;
        return r.length() > 300 ? r.substring(0, 300) + "…" : r;
    }

    private static String extractFenced(String s) {
        int a = s.indexOf("```");
        if (a < 0) return "";
        int b = s.indexOf('\n', a);
        int e = s.indexOf("```", b > 0 ? b : a + 3);
        if (b < 0 || e < 0) return "";
        String body = s.substring(b + 1, e).trim();
        return body.startsWith("json") ? body.substring(4).trim() : body;
    }

    private static String tryPrettyJson(String s) {
        try { return new JSONObject(s).toString(2); } catch (Throwable ignored) { }
        try { return new JSONArray(s).toString(2); } catch (Throwable ignored) { }
        return null;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static float parseFloat(String s, float def) {
        try { return Float.parseFloat(s.trim()); } catch (Throwable ignored) { return def; }
    }

    private static String safeName(String s, String def) {
        String t = s == null ? "" : s.trim().replaceAll("[^0-9A-Za-z_\\u4e00-\\u9fa5]", "_");
        return t.isEmpty() ? def : t;
    }

    private void copyResult() {
        String t = etResult.getText().toString();
        if (t.trim().isEmpty()) { toast(getString(R.string.ai_nothing)); return; }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("ai_anim", t));
            toast(getString(R.string.ai_copied));
        }
    }

    /** 保存：Android 10+ 写公共下载/MinewaysMobile/AI动画；低版本写应用外部目录。 */
    private void saveResult() {
        String t = etResult.getText().toString();
        if (t.trim().isEmpty()) { toast(getString(R.string.ai_nothing)); return; }
        int id = rgTarget.getCheckedRadioButtonId();
        boolean molang = id == R.id.rb_ai_molang;
        boolean particle = id == R.id.rb_ai_particle;
        String name = safeName(etName.getText().toString(), "logo");
        String fileName = "ai_" + name + (molang ? ".molang.txt" : (particle ? ".particle.json" : ".animation.json"));
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                cv.put(MediaStore.Downloads.MIME_TYPE, molang ? "text/plain" : "application/json");
                cv.put(MediaStore.Downloads.RELATIVE_PATH, "Download/MinewaysMobile/AI动画");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) throw new Exception("create failed");
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os == null) throw new Exception("open failed");
                    os.write(t.getBytes(StandardCharsets.UTF_8));
                }
                toast(getString(R.string.ai_saved_public, fileName));
            } else {
                File dir = getExternalFilesDir("AI动画");
                if (dir == null) dir = getFilesDir();
                File out = new File(dir, fileName);
                java.io.FileOutputStream fo = new java.io.FileOutputStream(out);
                fo.write(t.getBytes(StandardCharsets.UTF_8));
                fo.close();
                toast(getString(R.string.ai_saved_private, out.getAbsolutePath()));
            }
        } catch (Throwable e) {
            toast(getString(R.string.ai_save_failed, e.getMessage() == null ? "" : e.getMessage()));
        }
    }

    private void setBusy(boolean b) {
        findViewById(R.id.btn_ai_generate).setEnabled(!b);
        findViewById(R.id.btn_ai_copy).setEnabled(!b);
        findViewById(R.id.btn_ai_save).setEnabled(!b);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
