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
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

/**
 * 截屏悬浮预览：抓拍后在屏幕角落悬浮 2 秒，随后 0.4 秒渐出并自动保存到相册。
 * 点击悬浮图 → 进入编辑器。
 * 支持两种来源：文件路径（root screencap）或共享内存 Bitmap（SurfaceControl 直拍，毫秒级）。
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
    private Bitmap currentBitmap;
    private boolean finished = false;
    private boolean fileReady = false;
    private final Object fileLock = new Object();
    private int gen = 0;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        main = new Handler(getMainLooper());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        try {
            if (intent != null) {
                ParcelFileDescriptor pfd = intent.getParcelableExtra("shm");
                String path = intent.getStringExtra("path");
                if (pfd != null) showShm(pfd);
                else if (path != null) show(path);
            }
        } catch (Throwable t) {
            String p = intent != null ? intent.getStringExtra("path") : null;
            if (p != null) { try { MediaSaver.saveToGallery(this, p); } catch (Throwable ignored) {} }
            teardownView();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    /** 文件来源（root screencap）。 */
    private void show(final String path) {
        final int g;
        synchronized (this) { g = ++gen; }
        currentPath = path;
        finished = false;
        teardownView();

        if (!Settings.canDrawOverlays(this)) {
            openEditor(path);
            stopSelf();
            return;
        }

        WindowMetrics metrics = wm.getCurrentWindowMetrics();
        Rect b = metrics.getBounds();
        Bitmap thumb = decodeThumb(path, Math.round(b.width() * 0.30f));
        if (thumb == null) { saveAndFinish(path, g); return; }

        currentBitmap = thumb;
        synchronized (fileLock) { fileReady = true; }
        displayBitmap(thumb, g);
    }

    /** 共享内存来源（SurfaceControl 直拍）：立即显示 Bitmap，后台落盘供保存/编辑。 */
    private void showShm(final ParcelFileDescriptor pfd) {
        final Bitmap bmp;
        try {
            bmp = parseShm(pfd);
        } catch (Throwable t) {
            try { pfd.close(); } catch (Throwable ignored) {}
            teardownView();
            stopSelf();
            return;
        }
        try { pfd.close(); } catch (Throwable ignored) {}

        final int g;
        synchronized (this) { g = ++gen; }
        currentBitmap = bmp;
        finished = false;
        synchronized (fileLock) { fileReady = false; }
        teardownView();

        if (!Settings.canDrawOverlays(this)) {
            // 无悬浮权限：后台落盘后打开编辑器兜底
            new Thread(() -> {
                File f = persist(bmp, g);
                if (f != null) openEditor(f.getAbsolutePath());
                stopSelf();
            }).start();
            return;
        }

        displayBitmap(bmp, g);

        // 后台压缩落盘，完成后通知保存/编辑路径
        new Thread(() -> {
            File f = persist(bmp, g);
            if (f != null) currentPath = f.getAbsolutePath();
            synchronized (fileLock) {
                fileReady = true;
                fileLock.notifyAll();
            }
        }).start();
    }

    /** 核心：按宽高比布局、构建视图、添加窗口并启动倒计时。 */
    private void displayBitmap(Bitmap thumb, final int g) {
        WindowMetrics metrics = wm.getCurrentWindowMetrics();
        Rect b = metrics.getBounds();
        int sw = b.width(), sh = b.height();
        float maxW = sw * 0.30f, maxH = sh * 0.26f;

        float ar = thumb.getWidth() / (float) thumb.getHeight();
        float tw = maxW, th = maxH;
        if (tw / th > ar) tw = th * ar;
        else th = tw / ar;

        buildView(thumb, (int) tw, (int) th);

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
            final Bitmap tb = thumb;
            new Thread(() -> {
                File f = persist(tb, g);
                if (f != null) openEditor(f.getAbsolutePath());
                stopSelf();
            }).start();
        }
    }

    private void buildView(Bitmap thumb, int w, int h) {
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

        GradientDrawable stroke = new GradientDrawable();
        stroke.setCornerRadius(radius);
        stroke.setStroke(dp(1), 0x88FFFFFF);
        fl.setForeground(stroke);

        fl.setContentDescription("Screenshot preview, tap to edit");
        fl.setOnClickListener(v -> onPreviewClick());
        root = fl;
    }

    /** 点击：等待文件就绪后进入编辑器。 */
    private void onPreviewClick() {
        if (finished) return;
        finished = true;
        final int g;
        synchronized (this) { g = ++gen; }
        teardownView();
        new Thread(() -> {
            waitFileReady();
            final String p = currentPath;
            main.post(() -> {
                if (p != null) openEditor(p);
                stopSelf();
            });
        }).start();
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
                saveAndFadeOut(g);
            }
        });
        animator.start();
    }

    /** 无视图兜底（解码失败）：直接保存已有文件。 */
    private void saveAndFinish(final String path, final int g) {
        new Thread(() -> {
            boolean ok = false;
            try { MediaSaver.saveToGallery(this, path); ok = true; }
            catch (Throwable t) { ok = false; }
            final boolean saved = ok;
            main.post(() -> {
                Toast.makeText(this, saved ? R.string.saved : R.string.save_failed,
                        Toast.LENGTH_SHORT).show();
                if (g == gen) { teardownView(); stopSelf(); }
            });
        }).start();
    }

    /** 正常超时：0.4 秒渐出，结束后等文件就绪再保存相册。 */
    private void saveAndFadeOut(final int g) {
        if (root == null) { saveAndFinish(currentPath, g); return; }
        root.animate().cancel();
        root.animate().alpha(0f).setDuration(FADE_OUT)
                .setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator a) {
                        if (g != gen) return;
                        teardownView();
                        new Thread(() -> {
                            waitFileReady();
                            boolean ok = false;
                            try {
                                if (currentPath != null) {
                                    MediaSaver.saveToGallery(FloatingPreviewService.this, currentPath);
                                    ok = true;
                                }
                            } catch (Throwable t) { ok = false; }
                            final boolean saved = ok;
                            main.post(() -> {
                                Toast.makeText(FloatingPreviewService.this,
                                        saved ? R.string.saved : R.string.save_failed,
                                        Toast.LENGTH_SHORT).show();
                                stopSelf();
                            });
                        }).start();
                    }
                }).start();
    }

    /** 从共享内存解析 Bitmap（mmap 只读，无需 SharedMemory 隐藏方法）。 */
    private Bitmap parseShm(ParcelFileDescriptor pfd) throws Exception {
        StructStat st = Os.fstat(pfd.getFileDescriptor());
        long size = st.st_size;
        ByteBuffer bb = Os.mmap(0, size, OsConstants.PROT_READ,
                OsConstants.MAP_SHARED, pfd.getFileDescriptor(), 0);
        int w = bb.getInt(), h = bb.getInt(), bytes = bb.getInt();
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bmp.copyPixelsFromBuffer(bb);
        Os.munmap(bb, size);
        return bmp;
    }

    /** Bitmap 压缩为 PNG 写入缓存目录，返回文件（无需 root）。 */
    private File persist(Bitmap bmp, int g) {
        File f = new File(getCacheDir(), "shot_" + g + ".png");
        try (FileOutputStream fos = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    private void waitFileReady() {
        synchronized (fileLock) {
            long deadline = System.currentTimeMillis() + 3000L;
            while (!fileReady && System.currentTimeMillis() < deadline) {
                try { fileLock.wait(300L); } catch (Throwable t) { break; }
            }
        }
    }

    private void openEditor(String path) {
        Intent i = new Intent(this, EditorActivity.class);
        i.putExtra("path", path);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Throwable t) {
            new Thread(() -> {
                try { MediaSaver.saveToGallery(this, path); } catch (Throwable ignored) {}
            }).start();
        }
    }

    private Bitmap decodeThumb(String path, int targetW) {
        try {
            BitmapFactory.Options o = new Bitmap.BitmapFactory.Options = new BitmapFactory.Options();
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
            root.animate().cancel();
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
