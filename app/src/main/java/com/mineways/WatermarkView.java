package com.mineways;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * 界面背景水印（防泄密）——纯装饰层，不参与任何业务逻辑。
 * <p>
 * 安全性设计：
 * <ul>
 *   <li>不改动任何原有布局：把 activity 的内容整体包进一层 FrameLayout，水印作为它的兄弟层叠加；</li>
 *   <li>不拦截触摸/焦点：clickable/focusable 全关，事件照常落到下面的真实控件；</li>
 *   <li>所有异常都被吞掉，水印失败也绝不影响界面与功能；</li>
 *   <li>只有渲染，没有权限、没有文件、没有网络。</li>
 * </ul>
 * 用法：在 {@code setContentView(...)} 之后调用 {@code WatermarkView.attach(this)}。
 */
public class WatermarkView extends View {

    /** 水印文案（想换字样只改这里或 strings.xml 的 app_watermark）。 */
    private static final String DEFAULT_TEXT = "";

    /** 文字颜色与不透明度（0-255）；低调但截图/拍照时清晰可辨。 */
    private static final int TEXT_COLOR = Color.rgb(0x60, 0x60, 0x60);
    private static final int TEXT_ALPHA = 30;

    /** 平铺密度：值越大越稀疏。 */
    private static final float STEP_X_FACTOR = 5.0f;   // 横向：文字宽度 + N 倍字号
    private static final float STEP_Y_FACTOR = 6.0f;   // 纵向：N 倍字号

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String text;
    private final float textSize;
    private float stepX;
    private float stepY;

    public WatermarkView(Activity activity) {
        this(activity, null);
    }

    public WatermarkView(Activity activity, String text) {
        super(activity);
        String t = (text == null || text.trim().isEmpty())
                ? activity.getString(R.string.app_watermark)
                : text;
        this.text = t;
        Resources r = activity.getResources();
        this.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13,
                r.getDisplayMetrics());

        paint.setColor(TEXT_COLOR);
        paint.setAlpha(TEXT_ALPHA);
        paint.setTextSize(textSize);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setSubpixelText(true);

        float width = paint.measureText(this.text);
        stepX = width + textSize * STEP_X_FACTOR;
        stepY = textSize * STEP_Y_FACTOR;
        if (stepX <= 0) stepX = textSize * 8f;

        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setLongClickable(false);
        setDuplicateParentStateEnabled(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0 || text.isEmpty()) return;

        canvas.save();
        // 斜向平铺：旋转后需要覆盖对角线范围，故按对角线长度铺满
        canvas.rotate(-24, w / 2f, h / 2f);
        float span = (float) Math.hypot(w, h) + stepX * 2f;
        float left = w / 2f - span / 2f;
        float top = h / 2f - span / 2f;
        float right = w / 2f + span / 2f;
        float bottom = h / 2f + span / 2f;
        for (float y = top; y <= bottom; y += stepY) {
            for (float x = left; x <= right; x += stepX) {
                canvas.drawText(text, x, y, paint);
            }
        }
        canvas.restore();
    }

    /**
     * 把水印挂到 activity 界面上（叠加在内容之上，不拦截触摸）。
     * 任何异常都被吞掉：水印失败绝不影响界面与功能。
     */
    public static void attach(Activity activity) {
        attach(activity, null);
    }

    public static void attach(Activity activity, String text) {
        try {
            if (activity == null) return;
            ViewGroup contentRoot = activity.findViewById(android.R.id.content);
            if (contentRoot == null || contentRoot.getChildCount() == 0) return;
            View original = contentRoot.getChildAt(0);
            if (original == null) return;

            // 已经包过就不再重复叠加
            if (original instanceof FrameLayout && original.getTag(R.id.watermark_tag) != null) return;

            FrameLayout wrapper = new FrameLayout(activity);
            wrapper.setTag(R.id.watermark_tag, Boolean.TRUE);

            contentRoot.removeViewAt(0);
            wrapper.addView(original, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            WatermarkView wm = new WatermarkView(activity, text);
            wrapper.addView(wm, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            wm.bringToFront();

            contentRoot.addView(wrapper, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (Throwable ignored) {
            // 水印只是装饰，失败不影响任何功能
        }
    }
}
