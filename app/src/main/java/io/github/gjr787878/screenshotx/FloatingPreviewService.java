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
import android.os.Handler;
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

/**
 * 截屏悬浮预览：抓拍后在屏幕角落悬浮 2 秒，随后 0.4 秒渐出并自动保存到相册。
 * 点击悬浮图 → 进入编辑器；不点击 → 倒计时结束渐出并保存。
 * 悬浮窗不获取焦点（FLAG_NOT_FOCUSABLE），不打断当前应用，可在任意界面连续截图。
 *
 * 本服务不请求 Root：悬浮权限由 system_server 常驻 shell 预授权；
 * 若权限缺失则直接打开编辑器兜底，保证截图不丢失。
 */
public class FloatingPreviewService extends Service {

    private static final long DURATION = 2000L;
    private static final long FADE_OUT = 400L;

    private Handler main;
    private WindowManager wm;
    private View root;
    private View progressFill;
    private ValueAnimator animator;
    private String currentPath;
    private boolean finished = false;
    // 代际令牌：每次展示新预览 +1；后台保存完成后仅在仍是当前代时才拆除视图，
    // 避免连续截图时旧保存线程把新预览一并拆掉。
    private int gen = 0;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        // Handler 必须在 base Context attach 后（onCreate）初始化，
        // 字段初始化时 getMainLooper() 会因 mBase 为 null 抛 NPE
        main = new Handler(getMainLooper());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        try {
            if (intent != null) {
                String path = intent.getStringExtra("path");
                if (path != null) show(path);
            }
        } catch (Throwable t) {
            // 任意异常：尽力把截图存入相册，保证不丢失，且不弹崩溃框
            String p = intent != null ? intent.getStringExtra("path") : null;
            if (p != null) { try { MediaSaver.saveToGallery(this, p); } catch (Throwable ignored) {} }
            teardownView();
            stopSelf();
        }
        // 不自动重启：避免进程被杀后带空 intent 重启形成“屡次停止运行”循环
        return START_NOT_STICKY;
    }

    private void show(final String path) {
        final int g;
        synchronized (this) { g = ++gen; }
        currentPath = path;
        finished = false;
        teardownView(); // 当前在主线程，可直接移除

        if (!Settings.canDrawOverlays(this)) {
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
        if (thumb == null) { saveAndFinish(path, g); return; }

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
            startCountdown(g);
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
            synchronized (FloatingPreviewService.this) { gen++; } // 使任何在途保存失效
            teardownView();
            openEditor(path);
            stopSelf();
        });
        root = fl;
    }

    private void startCountdown(final int g) {
        progressFill.setScaleX(1f);
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DURATION);
        animator.addUpdateListener(a ->
                progressFill.setScaleX(1f - a.getAnimatedFraction()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                if (finished) return;
                finished = true;
                saveAndFadeOut(currentPath, g);
            }
        });
        animator.start();
    }

    /** 超时：后台保存到相册，回主线程后按代际决定是否移除悬浮图。 */
    private void saveAndFinish(final String path, final int g) {
        new Thread(() -> {
            boolean ok = false;
            try {
                MediaSaver.saveToGallery(FloatingPreviewService.this, path);
                ok = true;
            } catch (Throwable t) {
                ok = false;
            }
            final boolean saved = ok;
            main.post(() -> {
                // 提示不具破坏性，始终给出
                Toast.makeText(FloatingPreviewService.this,
                        saved ? "已保存到相册" : "自动保存失败", Toast.LENGTH_SHORT).show();
                if (g != gen) return; // 已有更新的预览接管，不拆新视图
                teardownView();
                stopSelf();
            });
        }).start();
    }

    /** 超时：先播放 0.4s 渐出（总停留 2.4 秒），渐出结束后再保存相册并移除悬浮图。 */
    private void saveAndFadeOut(final String path, final int g) {
        if (root == null) { saveAndFinish(path, g); return; }
        root.animate().cancel();
        root.animate().alpha(0f).setDuration(FADE_OUT)
                .setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator a) {
                        if (g != gen) return; // 已被新预览接管，旧视图已移除，不拆新视图
                        // 2.4 秒后：先移除视图，再后台保存到相册
                        teardownView();
                        new Thread(() -> {
                            boolean ok = false;
                            try {
                                MediaSaver.saveToGallery(FloatingPreviewService.this, path);
                                ok = true;
                            } catch (Throwable t) {
                                ok = false;
                            }
                            final boolean saved = ok;
                            main.post(() -> {
                                Toast.makeText(FloatingPreviewService.this,
                                        saved ? "已保存到相册" : "自动保存失败",
                                        Toast.LENGTH_SHORT).show();
                                stopSelf();
                            });
                        }).start();
                    }
                }).start();
    }

    private void openEditor(String path) {
        Intent i = new Intent(this, EditorActivity.class);
        i.putExtra("path", path);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Throwable t) {
            // 连编辑器都打不开：直接存相册兜底
            new Thread(() -> {
                try { MediaSaver.saveToGallery(this, path); } catch (Throwable ignored) {}
            }).start();
        }
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

    /** 仅在主线程调用：取消动画并移除悬浮窗。 */
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

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
