package io.github.gjr787878.screenshotx;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class ScreenshotService extends Service {

    public static final String ACTION_SHOOT = "io.github.gjr787878.screenshotx.SHOOT";

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
        String ch = "glassshot";
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(ch, "GlassShot", NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(channel);

        Intent shootIntent = new Intent(this, ScreenshotService.class);
        shootIntent.setAction(ACTION_SHOOT);
        PendingIntent shootPi = PendingIntent.getService(this, 0, shootIntent, PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, ch)
                .setContentTitle("GlassShot")
                .setContentText("点右边按钮截屏")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_camera, "截屏", shootPi).build())
                .setOngoing(true)
                .build();
    }

    private void shoot() {
        try {
            File tmp = new File(getCacheDir(), "shot.png");
            // 用 Root screencap
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("screencap -p " + tmp.getAbsolutePath() + "\n");
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();

            if (tmp.exists() && tmp.length() > 0) {
                Intent i = new Intent(this, EditorActivity.class);
                i.putExtra("path", tmp.getAbsolutePath());
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
