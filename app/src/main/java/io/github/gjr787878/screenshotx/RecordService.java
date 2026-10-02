package io.github.gjr787878.screenshotx;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.HandlerThread;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;
import android.graphics.Point;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 录屏服务（重写版，标准 MediaProjection 方案）：
 * - 视频：MediaCodec(AVC) + createInputSurface + VirtualDisplay 镜像屏幕
 * - 系统声音：AudioPlaybackCapture（tee 语义，只复制播放流，手机扬声器照常出声）
 *   喂给 MediaCodec(AAC)，与视频同进程直接 mux，音视频同步
 * - 输出先写 app 私有外部目录，停止后 MediaSaver 保存到相册（Movies/Screenshots）
 * - 不再需要 root screenrecord、REMOTE_SUBMIX、PCM 中转与手工合并
 */
public class RecordService extends Service {

    public static final String ACTION_START = "io.github.gjr787878.screenshotx.START";
    public static final String ACTION_STOP = "io.github.gjr787878.screenshotx.STOP";
    /** 录制未在进行（授权取消 / 录制结束）：通知 system_server 复位按键状态。 */
    public static final String ACTION_RECORD_ENDED =
            "io.github.gjr787878.screenshotx.RECORD_ENDED";
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_DATA = "resultData";
    public static final String CHANNEL_ID = "screenshotx_record";
    private static final String TAG = "ScreenshotX";

    private static final int SAMPLE_RATE = 44100;
    /** 音频轨等待就绪超时：开始录屏后 3s 内仍无音频格式（无媒体播放）则放弃音频轨。 */
    private static final long AUDIO_READY_TIMEOUT_MS = 3000;

    // 音频轨状态
    private static final int AUDIO_PENDING = 0;
    private static final int AUDIO_ACTIVE = 1;
    private static final int AUDIO_DROPPED = 2;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private MediaCodec videoEncoder;
    private MediaCodec audioEncoder;
    private AudioRecord audioRecord;
    private MediaMuxer muxer;
    private HandlerThread callbackThread;
    private Handler callbackHandler;
    private Thread drainThread;
    private Thread audioCaptureThread;

    private volatile boolean stopping = false;
    private volatile boolean muxerStarted = false;
    private final Object muxerLock = new Object();

    private int videoTrack = -1;
    private int audioState = AUDIO_PENDING;
    private int audioTrack = -1;
    private boolean wantAudio;
    private String outputPath;
    private int width, height, dpi;

    private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            Log.d(TAG, "projection onStop (user/system stopped, e.g. lockscreen)");
            requestStop("projection callback");
        }
    };

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        callbackThread = new HandlerThread("sx-proj-cb");
        callbackThread.start();
        callbackHandler = new Handler(callbackThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_START.equals(action)) {
            startRecording(intent);
        } else if (ACTION_STOP.equals(action)) {
            requestStop("action stop");
        }
        return START_NOT_STICKY;
    }

    private void startRecording(Intent intent) {
        if (mediaProjection != null || drainThread != null) return;

        final int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        final Intent data;
        if (Build.VERSION.SDK_INT >= 33) {
            data = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        } else {
            data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        }
        if (data == null) {
            Log.d(TAG, "start: no projection token, abort");
            stopSelf();
            return;
        }

        // Android 14+：必须先 startForeground(mediaProjection) 再 getMediaProjection
        startForeground(2, buildNotification("录屏中..."));

        try {
            // 真实屏幕尺寸 / dpi（偶数化）
            WindowManager wm = getSystemService(WindowManager.class);
            Point size = new Point();
            wm.getDefaultDisplay().getRealSize(size);
            width = size.x & ~1;
            height = size.y & ~1;
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            dpi = metrics.densityDpi;
            wantAudio = Prefs.recAudio(this);

            File dir = getExternalFilesDir(null);
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            outputPath = new File(dir, "sx_rec_" + ts + ".mp4").getAbsolutePath();

            MediaProjectionManager mpm = getSystemService(MediaProjectionManager.class);
            mediaProjection = mpm.getMediaProjection(resultCode, data);
            mediaProjection.registerCallback(projectionCallback, callbackHandler);

            // ---- 视频编码器（输入 Surface）----
            int bitrate = Prefs.recBitrate(this);
            MediaFormat vf = MediaFormat.createVideoFormat(
                    MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            vf.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            vf.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            vf.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            vf.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            vf.setInteger(MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
            videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            videoEncoder.configure(vf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Surface inputSurface = videoEncoder.createInputSurface();
            videoEncoder.start();

            // ---- 音频（内录）----
            if (wantAudio) {
                setupAudio();
            }

            muxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            // ---- 虚拟显示屏：屏幕镜像到编码器 Surface ----
            virtualDisplay = mediaProjection.createVirtualDisplay("ScreenshotX",
                    width, height, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    inputSurface, null, null);

            // 悬浮计时胶囊
            try {
                Intent fi = new Intent(this, FloatingRecordService.class)
                        .setAction(FloatingRecordService.ACTION_SHOW);
                startService(fi);
            } catch (Throwable ignored) {}

            // ---- 工作线程 ----
            stopping = false;
            muxerStarted = false;
            if (wantAudio && audioRecord != null) {
                audioCaptureThread = new Thread(this::captureAudioLoop, "sx-audio-cap");
                audioCaptureThread.start();
            } else {
                wantAudio = false;
            }
            drainThread = new Thread(this::drainLoop, "sx-drain");
            drainThread.start();
            Log.d(TAG, "recording started " + width + "x" + height + " audio=" + wantAudio);
        } catch (Throwable t) {
            Log.d(TAG, "start failed: " + t);
            releaseQuietly();
            sendBroadcast(new Intent(ACTION_RECORD_ENDED).setPackage(getPackageName()));
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    /**
     * 配置内录 AudioRecord（AudioPlaybackCapture）与 AAC 编码器。
     * 任何一步失败：清理已建资源、置空，调用方按无音频继续（视频照常）。
     */
    private void setupAudio() {
        try {
            AudioPlaybackCaptureConfiguration cfg =
                    new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                            .build();
            AudioFormat af = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build();
            audioRecord = new AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(cfg)
                    .setAudioFormat(af)
                    .build();
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException("AudioRecord not initialized");
            }

            MediaFormat aff = MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 2);
            aff.setInteger(MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            aff.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
            aff.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
            audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            audioEncoder.configure(aff, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            audioEncoder.start();
            audioRecord.startRecording();
        } catch (Throwable t) {
            Log.d(TAG, "setupAudio failed, continue without audio: " + t);
            try { if (audioEncoder != null) audioEncoder.release(); } catch (Throwable i) {}
            try { if (audioRecord != null) audioRecord.release(); } catch (Throwable i) {}
            audioEncoder = null;
            audioRecord = null;
        }
    }

    /** 音频 PCM 捕获 → AAC 编码器输入（input 端，与 drain 的 output 端并发，官方允许）。 */
    private void captureAudioLoop() {
        byte[] buf = new byte[8192];
        AtomicLong totalFrames = new AtomicLong(0);
        try {
            while (!stopping) {
                int n = audioRecord.read(buf, 0, buf.length);
                if (n > 0) {
                    int idx = audioEncoder.dequeueInputBuffer(10000);
                    if (idx >= 0) {
                        ByteBuffer ib = audioEncoder.getInputBuffer(idx);
                        ib.clear();
                        ib.put(buf, 0, n);
                        long pts = totalFrames.get() * 1000000L / SAMPLE_RATE;
                        audioEncoder.queueInputBuffer(idx, 0, n, pts, 0);
                        // 立体声 16bit：每样本帧 4 字节
                        totalFrames.addAndGet(n / 4);
                    }
                } else if (n < 0) {
                    break;
                }
            }
        } catch (Throwable t) {
            Log.d(TAG, "audio capture loop: " + t);
        } finally {
            // 保证向编码器发 EOS，drain 才能结束
            try {
                int idx = audioEncoder.dequeueInputBuffer(10000);
                if (idx >= 0) {
                    audioEncoder.queueInputBuffer(idx, 0, 0,
                            totalFrames.get() * 1000000L / SAMPLE_RATE,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }
            } catch (Throwable ignored) {}
        }
    }

    /** 统一抽取视频/音频编码器输出并写 muxer，直到双轨 EOS。 */
    private void drainLoop() {
        MediaCodec.BufferInfo vi = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo ai = new MediaCodec.BufferInfo();
        boolean vEos = false;
        boolean aEos = !wantAudio;
        long audioDeadline = 0;

        try {
            while (!vEos || !aEos) {
                if (!vEos) {
                    vEos = drainEncoder(videoEncoder, vi, true, 0);
                }
                if (wantAudio && !aEos) {
                    if (audioState == AUDIO_PENDING && videoTrack >= 0) {
                        long now = System.currentTimeMillis();
                        if (audioDeadline == 0) audioDeadline = now + AUDIO_READY_TIMEOUT_MS;
                        if (now > audioDeadline) {
                            // 迟迟无音频（无媒体播放）：放弃音频轨，停捕获
                            audioState = AUDIO_DROPPED;
                            Log.d(TAG, "audio track dropped (no playback at start)");
                            try { audioRecord.stop(); } catch (Throwable ignored) {}
                        }
                    }
                    aEos = drainEncoder(audioEncoder, ai, false, audioDeadline);
                }
                maybeStartMuxer();
            }
        } catch (Throwable t) {
            Log.d(TAG, "drain loop: " + t);
        }
        finalizeAndSave();
    }

    /**
     * 抽取单个编码器一次输出。
     * 视频：FORMAT_CHANGED 时加视频轨；音频：按 audioState 加轨 / 丢弃。
     * 返回是否收到 EOS。
     */
    private boolean drainEncoder(MediaCodec enc, MediaCodec.BufferInfo info,
            boolean video, long audioDeadline) {
        int idx = enc.dequeueOutputBuffer(info, 10000);
        if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
            return false;
        }
        if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            synchronized (muxerLock) {
                if (!muxerStarted && muxer != null) {
                    if (video) {
                        videoTrack = muxer.addTrack(enc.getOutputFormat());
                    } else if (audioState == AUDIO_PENDING) {
                        audioTrack = muxer.addTrack(enc.getOutputFormat());
                        audioState = AUDIO_ACTIVE;
                    }
                }
            }
            return false;
        }
        if (idx >= 0) {
            boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            boolean canWrite = info.size > 0 && muxerStarted;
            if (canWrite) {
                ByteBuffer out = enc.getOutputBuffer(idx);
                out.position(info.offset);
                out.limit(info.offset + info.size);
                synchronized (muxerLock) {
                    if (muxer != null) {
                        if (video) muxer.writeSampleData(videoTrack, out, info);
                        else if (audioState == AUDIO_ACTIVE)
                            muxer.writeSampleData(audioTrack, out, info);
                    }
                }
            }
            enc.releaseOutputBuffer(idx, false);
            return eos;
        }
        return false;
    }

    /** 视频轨就绪且音频轨已加/已放弃时启动 muxer（启动后不能再加轨）。 */
    private void maybeStartMuxer() {
        synchronized (muxerLock) {
            if (muxerStarted || muxer == null) return;
            boolean audioReady = !wantAudio
                    || audioState == AUDIO_ACTIVE || audioState == AUDIO_DROPPED;
            if (videoTrack >= 0 && audioReady) {
                muxer.start();
                muxerStarted = true;
                Log.d(TAG, "muxer started");
            }
        }
    }

    /** drain 结束：停 muxer → 释放资源 → 保存相册 → 复位/停服。 */
    private void finalizeAndSave() {
        synchronized (muxerLock) {
            try {
                if (muxerStarted) muxer.stop();
            } catch (Throwable t) {
                Log.d(TAG, "muxer stop: " + t);
            }
            try {
                if (muxer != null) muxer.release();
            } catch (Throwable ignored) {}
            muxer = null;
        }

        boolean saved = false;
        if (muxerStarted && outputPath != null) {
            try {
                MediaSaver.saveVideo(this, outputPath);
                saved = true;
                Log.d(TAG, "video saved to gallery");
            } catch (Throwable t) {
                Log.d(TAG, "save failed: " + t);
            }
        }

        releaseQuietly();
        if (saved && outputPath != null) {
            try { new File(outputPath).delete(); } catch (Throwable ignored) {}
        }

        sendBroadcast(new Intent(ACTION_RECORD_ENDED).setPackage(getPackageName()));
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    /** 请求停止（幂等，可由 ACTION_STOP 或 projection onStop 调用）。 */
    private void requestStop(String reason) {
        if (stopping) return;
        stopping = true;
        new Thread(() -> {
            Log.d(TAG, "stopping (" + reason + ")");
            try {
                startService(new Intent(this, FloatingRecordService.class)
                        .setAction(FloatingRecordService.ACTION_HIDE));
            } catch (Throwable ignored) {}
            // 解除音频捕获 read 阻塞，capture 线程退出并发音频 EOS
            try { if (audioRecord != null) audioRecord.stop(); } catch (Throwable ignored) {}
            // 视频 EOS（Surface 输入）
            try {
                if (videoEncoder != null) videoEncoder.signalEndOfInputStream();
            } catch (Throwable t) {
                Log.d(TAG, "signal EOS: " + t);
            }
            try {
                if (drainThread != null) drainThread.join(20000);
            } catch (Throwable ignored) {}
            // 兜底：drain 未正常结束，强制释放防泄漏
            if (drainThread != null && drainThread.isAlive()) {
                Log.d(TAG, "drain join timeout, force release");
                releaseQuietly();
                sendBroadcast(new Intent(ACTION_RECORD_ENDED).setPackage(getPackageName()));
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        }, "sx-stop").start();
    }

    /** 释放全部录制资源（尽量逐项，互不影响）。 */
    private void releaseQuietly() {
        try { if (virtualDisplay != null) virtualDisplay.release(); } catch (Throwable ignored) {}
        try { if (videoEncoder != null) { videoEncoder.stop(); videoEncoder.release(); } }
        catch (Throwable ignored) {
            try { if (videoEncoder != null) videoEncoder.release(); } catch (Throwable i) {}
        }
        try { if (audioEncoder != null) { audioEncoder.stop(); audioEncoder.release(); } }
        catch (Throwable ignored) {
            try { if (audioEncoder != null) audioEncoder.release(); } catch (Throwable i) {}
        }
        try { if (audioRecord != null) { audioRecord.stop(); audioRecord.release(); } }
        catch (Throwable ignored) {
            try { if (audioRecord != null) audioRecord.release(); } catch (Throwable i) {}
        }
        try {
            if (mediaProjection != null) {
                mediaProjection.unregisterCallback(projectionCallback);
                mediaProjection.stop();
            }
        } catch (Throwable ignored) {}
        virtualDisplay = null;
        videoEncoder = null;
        audioEncoder = null;
        audioRecord = null;
        mediaProjection = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseQuietly();
        try { if (callbackThread != null) callbackThread.quitSafely(); } catch (Throwable ignored) {}
    }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "录屏", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("ScreenshotX 录屏服务");
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification(String text) {
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("ScreenshotX")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .build();
    }
}
