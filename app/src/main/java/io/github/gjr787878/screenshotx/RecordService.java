package io.github.gjr787878.screenshotx;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.io.DataOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 录屏服务：
 * - 用 root shell screenrecord 录屏视频到临时文件
 * - 停止后通过 MediaSaver 保存到相册
 * - 管理悬浮窗控制（FloatingRecordService）
 *
 * 注意：当前版本只录视频，不录音频（screenrecord 限制）；
 * 音频录制后续用 MediaProjection 方案补充。
 */
public class RecordService extends Service {

    public static final String ACTION_START = "io.github.gjr787878.screenshotx.START";
    public static final String ACTION_STOP = "io.github.gjr787878.screenshotx.STOP";
    public static final String CHANNEL_ID = "screenshotx_record";

    private static Process recordProc;
    private static DataOutputStream recordOs;
    private static String outputPath;
    private static boolean recording = false;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_START.equals(action)) {
            startRecording();
        } else if (ACTION_STOP.equals(action)) {
            stopRecording();
        }
        return START_NOT_STICKY;
    }

    private void startRecording() {
        if (recording) return;
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        outputPath = "/data/local/tmp/screenshotx_rec_" + ts + ".mp4";
        try {
            // 启动常驻 root shell 录屏
            recordProc = Runtime.getRuntime().exec("su");
            recordOs = new DataOutputStream(recordProc.getOutputStream());
            // screenrecord 参数：竖屏、4Mbps、最长 30 分钟
            // 先杀掉所有旧的 screenrecord 进程，避免残留导致发热
            recordOs.writeBytes("pkill -f screenrecord\n");
            recordOs.flush();
            // screenrecord 前台运行（不加 &），这样 Ctrl+C 才能正确停止
            // 码率降到 2Mbps，分辨率 720p，减少发热和卡顿
            int bitrate = Prefs.recBitrate(this);
            recordOs.writeBytes("screenrecord --bit-rate " + bitrate
                    + " --size 720x1280 --time-limit 1800 "
                    + outputPath + "\n");
            recordOs.flush();
            recording = true;
            KeyInterceptor.setRecording(true);
            android.util.Log.d("ScreenshotX", "recording started: " + outputPath);

            // 启动前台通知
            Notification notif = buildNotification("录屏中...");
            startForeground(2, notif);

            // 启动悬浮窗控制
            Intent fi = new Intent(this, FloatingRecordService.class);
            fi.setAction(FloatingRecordService.ACTION_SHOW);
            startService(fi);
        } catch (Throwable t) {
            android.util.Log.d("ScreenshotX", "recording start failed: " + t);
            recording = false;
            KeyInterceptor.setRecording(false);
            stopSelf();
        }
    }

    private void stopRecording() {
        if (!recording) return;
        recording = false;
        KeyInterceptor.setRecording(false);
        // 保存到相册（后台线程）
        final Process oldProc = recordProc;
        final DataOutputStream oldOs = recordOs;
        recordProc = null;
        recordOs = null;

        // 立刻隐藏悬浮窗，不要等后台操作
        Intent fi = new Intent(this, FloatingRecordService.class);
        fi.setAction(FloatingRecordService.ACTION_HIDE);
        startService(fi);

        // 后台线程做杀进程和保存文件，不阻塞主线程
        new Thread(() -> {
            try {
                // 1. Ctrl+C 优雅停止
                if (oldOs != null) {
                    try { oldOs.write(3); oldOs.flush(); } catch (Throwable ignored) {}
                }
                // 2. 等 500ms 让 screenrecord 写完文件
                try { Thread.sleep(500); } catch (Throwable ignored) {}
                // 3. 兜底 pkill
                try {
                    Process killP = Runtime.getRuntime().exec("su");
                    DataOutputStream killOs = new DataOutputStream(killP.getOutputStream());
                    killOs.writeBytes("pkill -f screenrecord\n");
                    killOs.writeBytes("exit\n");
                    killOs.flush();
                    killP.waitFor();
                    killP.destroy();
                } catch (Throwable ignored) {}
                // 4. 销毁旧进程
                if (oldProc != null) {
                    try { oldProc.destroy(); } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }).start();

        android.util.Log.d("ScreenshotX", "recording stopped, saving: " + path);

        // 保存到相册（后台线程）
        // 保存到相册（后台线程）
        new Thread(() -> {
            try {
                // 先 chmod 让 MediaSaver 能读
                Process chmod = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(chmod.getOutputStream());
                os.writeBytes("chmod 666 " + path + "\n");
                os.writeBytes("exit\n");
                os.flush();
                chmod.waitFor();
                MediaSaver.saveVideo(this, path);
                android.util.Log.d("ScreenshotX", "video saved to gallery");
            } catch (Throwable t) {
                android.util.Log.d("ScreenshotX", "save video failed: " + t);
            }
        }).start();

        stopForeground(true);
        stopSelf();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "录屏", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("ScreenshotX 录屏服务");
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setContentTitle("ScreenshotX")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true);
        return b.build();
    }

    /** 供外部查询是否在录屏。 */
    public static boolean isRecording() {
        return recording;
    }
}
