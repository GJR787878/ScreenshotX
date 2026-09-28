package io.github.gjr787878.screenshotx;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.DataOutputStream;

/**
 * 截屏悬浮预览：抓拍后在屏幕角落悬浮约 2 秒。
 * 点击悬浮图 → 进入编辑器；不点击 → 倒计时结束自动保存到相册。
 * 悬浮窗不获取焦点（FLAG_NOT_FOCUSABLE），不打断当前应用，可在任意界面连续截图。
 */
public class FloatingPreviewService extends Service {

    private static final long DURATION = 2000L;

    private WindowManager wm;
    private View root;
    private View progressFill;
    private ValueAnimator animator;
    private String currentPath;
    private boolean finished = false;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null) {
            String path = intent.getStringExtra("path");
            if (path != null) show(path);
        }
        return START_STICKY;
    }

    private void show(final String path) {
        currentPath = path;
        finished = false;
        teardownView();

        if (!ensureOverlayPermission()) {
            // 无悬浮权限：退回直接打开编辑器，保证截图不丢失
            openEditor(path);
            stopSelf();
            return;
        }

        WindowMetrics metrics = wm.getCurrentWindowMetrics();
        Rect b = metrics.getBounds();
        int sw = b.width(), sh = b.height();
        float maxW = sw * 0.30f, maxH = sh * 0.26f;

        Bitmap thumb = decodeThumb(path, Math.round(maxW));
        if (thumb == null) { saveAndFinish(path); return; }

        // 按截图宽高比在最大框内 contain
        float ar = thumb.getWidth() / (float) thumb.getHeight();
        float tw = maxW, th = maxH;
        if (tw / th > ar) tw = th * ar;
        else th = tw / ar;

        buildView(thumb, (int) tw, (int) th, path);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                (int) tw, (int) th,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.x = dp(18);
        lp.y = dp(92);

        try {
            wm.addView(root, lp);
            startCountdown();
        } catch (Throwable t) {
            // 添加失败：退回直接打开编辑器
            openEditor(path);
            stopSelf();
        }
    }

    private void buildView(Bitmap thumb, int w, int h, final String path) {
        FrameLayout fl = new FrameLayout(this);
        fl.setElevation(dp(10));
        final int radius = dp(12);
        fl.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        fl.setClipToOutline(true);

        ImageView iv = new ImageView(this);
        iv.setImageBitmap(thumb);
        iv.setScaleType(ImageView.ScaleType.FIT_XY);
        fl.addView(iv, new FrameLayout.LayoutParams(-1, -1));

        // 倒计时进度条（底部细线，随时间收缩）
        View track = new View(this);
        track.setBackgroundColor(0x55000000);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-1, dp(3));
        tlp.gravity = Gravity.BOTTOM;
        fl.addView(track, tlp);

        progressFill = new View(this);
        progressFill.setBackgroundColor(Color.WHITE);
        progressFill.setPivotX(0f);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(-1, dp(3));
        flp.gravity = Gravity.BOTTOM;
        fl.addView(progressFill, flp);

        // 白色细边框
        GradientDrawable stroke = new GradientDrawable();
        stroke.setCornerRadius(radius);
        stroke.setStroke(dp(1), 0x88FFFFFF);
        fl.setForeground(stroke);

        fl.setContentDescription("截图预览，点击编辑");
        fl.setOnClickListener(v -> {
            if (finished) return;
            finished = true;
            teardownView();
            openEditor(path);
            stopSelf();
        });
        root = fl;
    }

    private void startCountdown() {
        progressFill.setScaleX(1f);
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DURATION);
        animator.addUpdateListener(a ->
                progressFill.setScaleX(1f - a.getAnimatedFraction()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                if (finished) return;
                finished = true;
                saveAndFinish(currentPath);
            }
        });
        animator.start();
    }

    /** 超时：后台保存到相册后移除悬浮图。 */
    private void saveAndFinish(final String path) {
        new Thread(() -> {
            try {
                MediaSaver.saveToGallery(FloatingPreviewService.this, path);
                toast("已保存到相册");
            } catch (Throwable t) {
                toast("自动保存失败");
            }
            teardownView();
            stopSelf();
        }).start();
    }

    private void openEditor(String path) {
        Intent i = new Intent(this, EditorActivity.class);
        i.putExtra("path", path);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Throwable t) {
            saveAndFinish(path);
        }
    }

    /** 悬浮窗权限：已授予则直接用；否则尝试经 Root 静默授权。 */
    private boolean ensureOverlayPermission() {
        if (Settings.canDrawOverlays(this)) return true;
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream o = new DataOutputStream(p.getOutputStream());
            o.writeBytes("appops set " + getPackageName()
                    + " SYSTEM_ALERT_WINDOW allow\n");
            o.writeBytes("exit\n");
            o.flush();
            p.waitFor();
        } catch (Throwable ignored) {}
        return Settings.canDrawOverlays(this);
    }

    private Bitmap decodeThumb(String path, int targetW) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= targetW) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(path, o);
        } catch (Throwable t) {
            return null;
        }
    }

    private void teardownView() {
        if (animator != null) { animator.cancel(); animator = null; }
        if (root != null) {
            try { wm.removeView(root); } catch (Throwable ignored) {}
            root = null;
        }
    }

    @Override public void onDestroy() {
        teardownView();
        super.onDestroy();
    }

    private void toast(final String s) {
        android.os.Handler h = new android.os.Handler(getMainLooper());
        h.post(() -> Toast.makeText(this, s, Toast.LENGTH_SHORT).show());
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
