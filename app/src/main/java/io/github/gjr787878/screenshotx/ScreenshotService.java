package io.github.gjr787878.screenshotx;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.io.DataOutputStream;

public class ScreenshotService extends Service {

    public static final String ACTION_SHOOT = "io.github.gjr787878.screenshotx.SHOOT";
    private static final String SHOT = "/data/local/tmp/screenshotx_shot.png";

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && ACTION_SHOOT.equals(intent.getAction())) {
            new Thread(this::shoot).start();
        }
        return START_NOT_STICKY;
    }

    private void shoot() {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("rm -f " + SHOT + "\n");
            os.writeBytes("screencap -p " + SHOT + "\n");
            os.writeBytes("chmod 666 " + SHOT + "\n");
            // 从 shell(uid) 启动编辑器，拥有后台启动 activity 权限
            os.writeBytes("am start -n io.github.gjr787878.screenshotx/.EditorActivity "
                    + "--es path " + SHOT + "\n");
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        stopSelf();
    }
}
