package io.github.gjr787878.screenshotx;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.io.DataOutputStream;
import java.io.InputStream;

/**
 * 截屏服务（前台服务，specialUse）。
 *
 * 作用：
 *  1. 以前台服务保持 App 进程常驻并处于“前台”状态，使 system_server 抓拍后能直接
 *     startService 拉起悬浮预览，根除 BackgroundServiceStartNotAllowedException；
 *  2. 进程内常驻 Root shell（开机/首次弹一次 Magisk 授权，之后复用，不再弹超级用户通知），
 *     在 system_server SurfaceControl 直拍失败时作为 ACTION_SHOOT 兜底抓拍。
 *
 * 通知渠道为 IMPORTANCE_MIN：不发声、不显示状态栏图标，折叠在通知栏最底部，基本无感。
 */
public class ScreenshotService extends Service {

    public static final String ACTION_SHOOT = "io.github.gjr787878.screenshotx.SHOOT";
    private static final String SHOT = "/data/local/tmp/screenshotx_shot.png";
    private static final String CHANNEL = "screenshotx_keep";
    private static final int NOTIF_ID = 0x55;

    private Process rootProc;
    private DataOutputStream rootOs;

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        startAsForeground();
        new Thread(this::ensureRoot).start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        startAsForeground();
        if (intent != null && ACTION_SHOOT.equals(intent.getAction())) {
            new Thread(this::shoot).start();
        } else {
            // 预热：确保 root shell 存在并保持进程常驻，后续截图复用，不再弹 Magisk 通知
            new Thread(this::ensureRoot).start();
        }
        return START_STICKY;
    }

    private void startAsForeground() {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel c = new NotificationChannel(CHANNEL,
                    getString(R.string.notif_channel), NotificationManager.IMPORTANCE_MIN);
            c.setShowBadge(false);
            c.setSound(null, null);
            c.enableLights(false);
            c.enableVibration(false);
            nm.createNotificationChannel(c);
            Notification n = new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.notif_text))
                    .setOngoing(true)
                    .setPriority(Notification.PRIORITY_MIN)
                    .build();
            startForeground(NOTIF_ID, n);
        } catch (Throwable t) {
            android.util.Log.e("ScreenshotX", "startForeground failed: " + t);
        }
    }

    private synchronized boolean ensureRoot() {
        if (rootProc != null && rootOs != null) return true;
        try {
            rootProc = Runtime.getRuntime().exec("su");
            rootOs = new DataOutputStream(rootProc.getOutputStream());
            drain(rootProc.getInputStream());
            drain(rootProc.getErrorStream());
            // 预授权悬浮窗权限
            rootOs.writeBytes("appops set io.github.gjr787878.screenshotx"
                    + " SYSTEM_ALERT_WINDOW allow\n");
            rootOs.flush();
            return true;
        } catch (Throwable t) {
            rootProc = null;
            rootOs = null;
            return false;
        }
    }

    private void drain(InputStream is) {
        new Thread(() -> {
            try {
                byte[] b = new byte[1024];
                while (is.read(b) > 0) { }
            } catch (Throwable ignored) { }
        }).start();
    }

    private synchronized void rootCmd(String cmd) throws Throwable {
        if (rootOs == null) throw new IllegalStateException("root shell not ready");
        rootOs.writeBytes(cmd + "\n");
        rootOs.flush();
    }

    private void shoot() {
        if (!ensureRoot()) return;
        try {
            doShoot();
        } catch (Throwable t) {
            // root 会话可能已断开，重建后重试一次
            rootProc = null;
            rootOs = null;
            if (ensureRoot()) {
                try { doShoot(); } catch (Throwable ignored) { }
            }
        }
    }

    private void doShoot() throws Throwable {
        rootCmd("rm -f " + SHOT);
        rootCmd("screencap -p " + SHOT);
        rootCmd("chmod 666 " + SHOT);
        // 抓拍后先悬浮预览（点击进编辑、超时自动存相册）；同进程内直接 startService
        Intent fp = new Intent(this, FloatingPreviewService.class);
        fp.putExtra("path", SHOT);
        startService(fp);
    }
}
