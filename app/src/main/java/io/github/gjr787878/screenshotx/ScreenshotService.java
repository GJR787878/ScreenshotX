package io.github.gjr787878.screenshotx;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.io.DataOutputStream;
import java.io.InputStream;

/**
 * 截屏服务：正常流程下抓拍由 system_server 常驻 Root shell 完成，
 * 本服务仅用于保持 App 进程常驻（加快悬浮预览/编辑器冷启动），
 * 以及在 system_server shell 不可用时作为 ACTION_SHOOT 兜底（此时才请求 su）。
 * 预热路径刻意不请求 su，避免每次进程重建都弹 Magisk 授权提示。
 */
public class ScreenshotService extends Service {

    public static final String ACTION_SHOOT = "io.github.gjr787878.screenshotx.SHOOT";
    private static final String SHOT = "/data/local/tmp/screenshotx_shot.png";

    private Process rootProc;
    private DataOutputStream rootOs;

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        // App 进程一创建就建 root shell（开机/首次拉起时弹一次 Magisk 授权，后续复用）；
        // system_server 常驻 shell 不可用时，本服务就是兜底抓拍路径，必须有 root。
        new Thread(this::ensureRoot).start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && ACTION_SHOOT.equals(intent.getAction())) {
            new Thread(this::shoot).start();
        } else {
            // 预热：确保 root shell 存在并保持进程常驻，后续截图复用不再弹 Magisk 通知
            new Thread(this::ensureRoot).start();
        }
        // START_STICKY：保持服务常驻，root shell 不释放，避免每次截图重建进程重复 exec su
        return START_STICKY;
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
        // 抓拍后先悬浮预览（点击进编辑、超时自动存相册）
        rootCmd("am startservice -n io.github.gjr787878.screenshotx/.FloatingPreviewService"
                + " --es path " + SHOT);
    }
}
