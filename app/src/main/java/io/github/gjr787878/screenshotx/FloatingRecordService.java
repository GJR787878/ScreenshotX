package io.github.gjr787878.screenshotx;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 录屏悬浮窗控制：
 * - 深色圆角胶囊，左上角红点，右上角计时器
 * - 下方圆形按钮：麦克风 / 停止（红色） / 关闭（X）
 * - 设置 FLAG_SECURE，录屏时悬浮窗不会被录进去（显示为黑色块）
 * - 可拖动位置
 */
public class FloatingRecordService extends Service {

    public static final String ACTION_SHOW = "io.github.gjr787878.screenshotx.SHOW";
    public static final String ACTION_HIDE = "io.github.gjr787878.screenshotx.HIDE";

    private WindowManager wm;
    private View capsule;
    private TextView timerTv;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private long startTime;
    private boolean micOn = true;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (timerTv == null) return;
            long elapsed = System.currentTimeMillis() - startTime;
            long sec = elapsed / 1000;
            String t = String.format("%02d:%02d", sec / 60, sec % 60);
            timerTv.setText(t);
            timerHandler.postDelayed(this, 1000);
        }
    };

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_SHOW;
        if (ACTION_SHOW.equals(action)) {
            show();
        } else if (ACTION_HIDE.equals(action)) {
            hide();
        }
        return START_NOT_STICKY;
    }

    private void show() {
        if (capsule != null) return;
        wm = getSystemService(WindowManager.class);
        startTime = System.currentTimeMillis();

        // 根布局：深色圆角胶囊
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundDrawable(roundBg(0xCC1A1A1A, dp(28)));
        root.setPadding(dp(16), dp(12), dp(16), dp(12));

        // 顶部行：红点 + 计时器
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        // 红点
        View dot = new View(this);
        FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(dp(10), dp(10));
        dot.setBackgroundDrawable(roundBg(0xFFFF3B30, dp(5)));
        topRow.addView(dot, dotLp);

        // 计时器
        timerTv = new TextView(this);
        timerTv.setText("00:00");
        timerTv.setTextColor(Color.WHITE);
        timerTv.setTextSize(16);
        timerTv.setPadding(dp(8), 0, 0, 0);
        topRow.addView(timerTv);

        root.addView(topRow);

        // 按钮行：麦克风 / 停止 / 关闭
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(12), 0, 0);

        // 麦克风按钮
        Button micBtn = circleBtn("🎙", 0x33FFFFFF);
        micBtn.setOnClickListener(v -> {
            micOn = !micOn;
            micBtn.setText(micOn ? "🎙" : "🔇");
        });
        btnRow.addView(micBtn);

        // 停止按钮（红色）
        Button stopBtn = circleBtn("●", 0xFFFF3B30);
        stopBtn.setOnClickListener(v -> stopRecording());
        btnRow.addView(stopBtn, marginLp(dp(12), 0, 0, 0));

        // 关闭按钮（X）
        Button closeBtn = circleBtn("✕", 0x33FFFFFF);
        closeBtn.setOnClickListener(v -> stopRecording());
        btnRow.addView(closeBtn, marginLp(dp(12), 0, 0, 0));

        root.addView(btnRow);

        // 窗口参数：TYPE_APPLICATION_OVERLAY，FLAG_SECURE 防止被录进去
        int type = android.os.Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = dp(80);

        wm.addView(root, lp);
        capsule = root;
        timerHandler.post(tick);

        // 简单拖动支持（按下后移动）
        root.setOnTouchListener(new android.view.View.OnTouchListener() {
            int startX, startY;
            float touchX, touchY;
            boolean dragging;
            @Override public boolean onTouch(View v, android.view.MotionEvent e) {
                switch (e.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        startX = lp.x; startY = lp.y;
                        touchX = e.getRawX(); touchY = e.getRawY();
                        dragging = true;
                        return true;
                    case android.view.MotionEvent.ACTION_MOVE:
                        if (dragging) {
                            lp.x = startX + (int)(e.getRawX() - touchX);
                            lp.y = startY + (int)(e.getRawY() - touchY);
                            wm.updateViewLayout(capsule, lp);
                        }
                        return true;
                    case android.view.MotionEvent.ACTION_UP:
                        dragging = false;
                        return true;
                }
                return false;
            }
        });
    }

    private void hide() {
        timerHandler.removeCallbacks(tick);
        if (capsule != null && wm != null) {
            try { wm.removeView(capsule); } catch (Throwable ignored) {}
        }
        capsule = null;
        stopSelf();
    }

    private void stopRecording() {
        Intent i = new Intent(this, RecordService.class);
        i.setAction(RecordService.ACTION_STOP);
        startService(i);
    }

    private Button circleBtn(String text, int bgColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackgroundDrawable(roundBg(bgColor, dp(20)));
        b.setMinWidth(dp(40));
        b.setMinHeight(dp(40));
        b.setPadding(0, 0, 0, 0);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(40), dp(40));
        b.setLayoutParams(lp);
        return b;
    }

    private android.graphics.drawable.Drawable roundBg(int color, int radius) {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(radius);
        return gd;
    }

    private LinearLayout.LayoutParams marginLp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(l, t, r, b);
        return lp;
    }

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density);
    }
}
