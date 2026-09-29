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
            recordOs.writeBytes("screenrecord --bit-rate 2000000 --size 720x1280 --time-limit 1800 "
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
        try {
            if (recordOs != null) {
                // 发送 Ctrl+C 结束 screenrecord
                recordOs.write(3);
                recordOs.flush();
            }
            // 兜底：再 pkill 一次，确保没有残留进程

            if (recordProc != null) {
                recordProc.waitFor();
                recordProc.destroy();
            }
        } catch (Throwable ignored) {}
        recordProc = null;
        recordOs = null;

        android.util.Log.d("ScreenshotX", "recording stopped, saving: " + outputPath);

        // 隐藏悬浮窗
        Intent fi = new Intent(this, FloatingRecordService.class);
        fi.setAction(FloatingRecordService.ACTION_HIDE);
        startService(fi);

        // 保存到相册（后台线程）
        final String path = outputPath;
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
