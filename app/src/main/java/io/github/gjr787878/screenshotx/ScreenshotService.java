package io.github.gjr787878.screenshotx;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.io.DataOutputStream;
import java.io.InputStream;

/**
 * 截屏服务：常驻进程 + 持久 Root shell，避免每次触发都冷启动 su。
 * 由 system_server 的 hook 或开机接收器启动。
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
        new Thread(this::ensureRoot).start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && ACTION_SHOOT.equals(intent.getAction())) {
            new Thread(this::shoot).start();
        } else {
            // 预热：建立 root 会话并保持进程常驻，加快后续触发
            new Thread(this::ensureRoot).start();
        }
        return START_STICKY;
    }

    private synchronized boolean ensureRoot() {
        if (rootProc != null && rootOs != null) return true;
        try {
            rootProc = Runtime.getRuntime().exec("su");
            rootOs = new DataOutputStream(rootProc.getOutputStream());
            drain(rootProc.getInputStream());
            drain(rootProc.getErrorStream());
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
        rootCmd("am start -n io.github.gjr787878.screenshotx/.EditorActivity --es path " + SHOT);
    }
}
