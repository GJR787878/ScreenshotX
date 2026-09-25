package io.github.gjr787878.screenshotx;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public class ScreenshotService extends Service {

    public static final String ACTION_SHOOT = "io.github.gjr787878.screenshotx.SHOOT";
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_DATA = "resultData";

    private MediaProjection mediaProjection;
    private int width, height, density;

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(1, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);

        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        density = metrics.densityDpi;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        if (intent == null) return START_STICKY;

        if (intent.hasExtra(EXTRA_RESULT_CODE)) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            mediaProjection = mpm.getMediaProjection(resultCode, resultData);
        }

        if (ACTION_SHOOT.equals(intent.getAction())) {
            takeScreenshot();
        }
        return START_STICKY;
    }

    private Notification buildNotification() {
        String ch = "screenshotx";
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(ch, "ScreenshotX", NotificationManager.IMPORTANCE_LOW));

        Intent shootIntent = new Intent(this, ScreenshotService.class);
        shootIntent.setAction(ACTION_SHOOT);
        PendingIntent pi = PendingIntent.getService(this, 0, shootIntent, PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, ch)
                .setContentTitle("ScreenshotX")
                .setContentText("点按钮截屏")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .addAction(android.R.drawable.ic_menu_camera, "截屏", pi)
                .setOngoing(true)
                .build();
    }

    private void takeScreenshot() {
        if (mediaProjection == null) {
            Toast.makeText(this, "请先在 App 里授权截屏权限", Toast.LENGTH_LONG).show();
            return;
        }

        ImageReader imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        VirtualDisplay virtualDisplay = mediaProjection.createVirtualDisplay("ScreenshotX",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);

        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image == null) return;

                Image.Plane[] planes = image.getPlanes();
                ByteBuffer buffer = planes[0].getBuffer();
                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - pixelStride * width;

                Bitmap bmp = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
                bmp.copyPixelsFromBuffer(buffer);
                Bitmap cropped = Bitmap.createBitmap(bmp, 0, 0, width, height);

                File tmp = new File(getCacheDir(), "shot.png");
                FileOutputStream fos = new FileOutputStream(tmp);
                cropped.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.close();

                Intent i = new Intent(this, EditorActivity.class);
                i.putExtra("path", tmp.getAbsolutePath());
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "截屏失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            } finally {
                if (image != null) image.close();
                if (virtualDisplay != null) virtualDisplay.release();
            }
        }, new Handler(Looper.getMainLooper()));
    }
}
