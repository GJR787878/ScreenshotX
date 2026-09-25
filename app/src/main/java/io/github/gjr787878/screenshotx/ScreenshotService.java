package io.github.gjr787878.screenshotx;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.widget.Toast;

import java.io.DataOutputStream;

public class ScreenshotService extends Service {

    public static final String ACTION_SHOOT = "io.github.gjr787878.screenshotx.SHOOT";
    private static final String TMP_PATH = "/data/local/tmp/screenshotx_shot.png";

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(1, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        if (i != null && ACTION_SHOOT.equals(i.getAction())) {
            shoot();
        }
        return START_STICKY;
    }

    private Notification buildNotification() {
        String ch = "screenshotx";
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(ch, "ScreenshotX", NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(channel);

        Intent shootIntent = new Intent(this, ScreenshotService.class);
        shootIntent.setAction(ACTION_SHOOT);
        PendingIntent shootPi = PendingIntent.getService(this, 0, shootIntent, PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, ch)
                .setContentTitle("ScreenshotX")
                .setContentText("点右边按钮截屏")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_camera, "截屏", shootPi).build())
                .setOngoing(true)
                .build();
    }

    private void shoot() {
        try {
            // 先删旧文件
            Runtime.getRuntime().exec(new String[]{"su", "-c", "rm -f " + TMP_PATH}).waitFor();

            // 用 Root screencap 保存到 /data/local/tmp（root 可写）
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("screencap -p " + TMP_PATH + "\n");
            os.writeBytes("chmod 666 " + TMP_PATH + "\n");
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();

            java.io.File tmp = new java.io.File(TMP_PATH);
            if (tmp.exists() && tmp.length() > 0) {
                Intent i = new Intent(this, EditorActivity.class);
                i.putExtra("path", TMP_PATH);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } else {
                Toast.makeText(this, "截屏失败，检查 Root 权限", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
