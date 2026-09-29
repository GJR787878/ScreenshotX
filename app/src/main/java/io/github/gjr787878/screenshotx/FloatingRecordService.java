package io.github.gjr787878.screenshotx;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 录屏悬浮小胶囊：
 * - 只有红点 + 计时器（MM:SS），深色圆角背景
 * - 按住拖动位置，单击结束录屏
 * - 不加 FLAG_SECURE，会被录进去（用户要求）
 */
public class FloatingRecordService extends Service {

    public static final String ACTION_SHOW = "io.github.gjr787878.screenshotx.SHOW";
    public static final String ACTION_HIDE = "io.github.gjr787878.screenshotx.HIDE";

    private WindowManager wm;
    private View capsule;
    private TextView timerTv;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private long startTime;

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

        // 胶囊：横向 LinearLayout，红点 + 时间
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setBackgroundDrawable(roundBg(0xCC1A1A1A, dp(16)));
        root.setPadding(dp(12), dp(6), dp(12), dp(6));

        // 红点
        View dot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dot.setBackgroundDrawable(roundBg(0xFFFF3B30, dp(4)));
        root.addView(dot, dotLp);

        // 计时器
        timerTv = new TextView(this);
        timerTv.setText("00:00");
        timerTv.setTextColor(Color.WHITE);
        timerTv.setTextSize(13);
        timerTv.setPadding(dp(6), 0, 0, 0);
        root.addView(timerTv);

        // 窗口参数：不加 FLAG_SECURE，会被录进去
        int type = android.os.Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = dp(80);

        wm.addView(root, lp);
        capsule = root;
        timerHandler.post(tick);

        // 拖动 + 单击结束
        root.setOnTouchListener(new View.OnTouchListener() {
            int startX, startY;
            float touchX, touchY;
            boolean dragging;
            long downTime;
            int totalMoved;

            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = lp.x; startY = lp.y;
                        touchX = e.getRawX(); touchY = e.getRawY();
                        downTime = System.currentTimeMillis();
                        totalMoved = 0;
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int)(e.getRawX() - touchX);
                        int dy = (int)(e.getRawY() - touchY);
                        if (Math.abs(dx) > 5 || Math.abs(dy) > 5) dragging = true;
                        if (dragging) {
                            totalMoved = Math.abs(dx) + Math.abs(dy);
                            lp.x = startX + dx;
                            lp.y = startY + dy;
                            wm.updateViewLayout(capsule, lp);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        long dur = System.currentTimeMillis() - downTime;
                        // 短按且没怎么移动 = 单击，结束录屏
                        if (dur < 300 && totalMoved < 10) {
                            stopRecording();
                        }
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

    private android.graphics.drawable.Drawable roundBg(int color, int radius) {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(radius);
        return gd;
    }

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density);
    }
}
