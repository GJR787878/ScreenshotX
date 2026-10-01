package io.github.gjr787878.screenshotx;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 录屏服务：
 * - 用 root shell screenrecord 录屏视频到 /data/local/tmp
 * - 常驻单个 su 会话（只在首次启动时请求一次 root，之后复用，避免频繁授权提示）
 * - 系统声音：system_server 用 REMOTE_SUBMIX 录 PCM，本服务停止时编码 AAC 并合并
 * - 停止后通过 MediaSaver 保存到相册（Movies/Screenshots）
 */
public class RecordService extends Service {

    public static final String ACTION_START = "io.github.gjr787878.screenshotx.START";
    public static final String ACTION_STOP = "io.github.gjr787878.screenshotx.STOP";
    public static final String CHANNEL_ID = "screenshotx_record";

    // 常驻 root shell（静态，跨多次录屏复用，避免重复触发 Magisk 授权提示）
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

    /** 确保常驻 su 已就绪（只创建一次）。 */
    private static synchronized void ensureSu() {
        try {
            if (recordProc != null && recordProc.isAlive()) return;
            recordProc = Runtime.getRuntime().exec("su");
            recordOs = new DataOutputStream(recordProc.getOutputStream());
        } catch (Throwable t) {
            Log.d("ScreenshotX", "ensureSu failed: " + t);
            recordProc = null;
            recordOs = null;
        }
    }

    /** 往常驻 su 会话写一条命令。 */
    private static void suWrite(String cmd) {
        try {
            if (recordOs == null) return;
            recordOs.writeBytes(cmd + "\n");
            recordOs.flush();
        } catch (Throwable ignored) {}
    }

    private void startRecording() {
        if (recording) return;
        ensureSu();
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        outputPath = "/data/local/tmp/screenshotx_rec_" + ts + ".mp4";
        try {
            // 先杀掉所有旧的 screenrecord 进程和 PID 文件
            suWrite("pkill -f screenrecord");
            suWrite("rm -f /data/local/tmp/sx_rec.pid");
            // screenrecord 后台运行并记录 PID。停止时用 kill -2 (SIGINT) 正常收尾
            int bitrate = Prefs.recBitrate(this);
            suWrite("screenrecord --bit-rate " + bitrate
                    + " --size 720x1280 --time-limit 1800 "
                    + outputPath + " & echo $! > /data/local/tmp/sx_rec.pid");
            recording = true;
            KeyInterceptor.setRecording(true);
            android.util.Log.d("ScreenshotX", "recording started: " + outputPath);

            Notification notif = buildNotification("录屏中...");
            startForeground(2, notif);

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
        final String path = outputPath;
        outputPath = null;

        // 立刻隐藏悬浮窗
        Intent fi = new Intent(this, FloatingRecordService.class);
        fi.setAction(FloatingRecordService.ACTION_HIDE);
        startService(fi);

        // 后台线程：收尾 screenrecord → 停音频 → 合并 → 保存（不阻塞主线程）
        new Thread(() -> {
            try {
                // 1. 用同一个常驻 su 会话：SIGINT 正常收尾 screenrecord
                if (recordOs != null) {
                    suWrite("kill -2 $(cat /data/local/tmp/sx_rec.pid 2>/dev/null) 2>/dev/null");
                    try { Thread.sleep(2000); } catch (Throwable ignored) {}
                    suWrite("kill -2 $(cat /data/local/tmp/sx_rec.pid 2>/dev/null) 2>/dev/null");
                    suWrite("sleep 1");
                    suWrite("pkill -f screenrecord");
                    // chmod 让 app 进程可读视频文件
                    suWrite("chmod 666 " + path);
                }
                // 2. 通知 system_server 停音频录制（悬浮窗停止路径；电源键路径已停过，幂等）
                try {
                    Intent br = new Intent("io.github.gjr787878.screenshotx.STOP_AUDIO");
                    sendBroadcast(br);
                } catch (Throwable ignored) {}
                // 3. 等音频停止并写完（PCM flush + chmod）
                try { Thread.sleep(2000); } catch (Throwable ignored) {}

                // 4. 有系统声音且 PCM 有效则编码 AAC 并合并
                String finalPath = path;
                File pcm = new File("/data/local/tmp/sx_audio.pcm");
                if (Prefs.recAudio(this) && pcm.exists() && pcm.length() > 1024) {
                    try {
                        String out = getCacheDir() + "/sx_final_" + System.currentTimeMillis() + ".mp4";
                        String aac = getCacheDir() + "/sx_audio_" + System.currentTimeMillis() + ".aac";
                        encodePcmToAac(pcm.getAbsolutePath(), aac);
                        muxVideoAudio(path, aac, out);
                        finalPath = out;
                        android.util.Log.d("ScreenshotX", "audio merged: " + out);
                    } catch (Throwable t) {
                        android.util.Log.d("ScreenshotX", "audio merge failed, keep video only: " + t);
                    }
                }
                // 5. 保存到相册
                MediaSaver.saveVideo(this, finalPath);
                android.util.Log.d("ScreenshotX", "video saved to gallery");
            } catch (Throwable t) {
                android.util.Log.d("ScreenshotX", "stop/save failed: " + t);
            }
        }).start();

        stopForeground(true);
        stopSelf();
    }

    /** PCM(44.1kHz mono 16bit) → AAC(ADTS) 文件。 */
    private static void encodePcmToAac(String pcmPath, String aacPath) throws Exception {
        MediaCodec codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        MediaFormat fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 1);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        fmt.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        codec.start();

        ByteBuffer[] inBufs = codec.getInputBuffers();
        ByteBuffer[] outBufs = codec.getOutputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        FileInputStream fis = new FileInputStream(pcmPath);
        FileOutputStream fos = new FileOutputStream(aacPath);
        byte[] pcmBuf = new byte[65536];
        long ptsUs = 0;
        boolean inputEos = false;
        boolean outputEos = false;

        while (!outputEos) {
            if (!inputEos) {
                int inIdx = codec.dequeueInputBuffer(10000);
                if (inIdx >= 0) {
                    ByteBuffer inBuf = inBufs[inIdx];
                    inBuf.clear();
                    int n = fis.read(pcmBuf, 0, Math.min(inBuf.remaining(), pcmBuf.length));
                    if (n < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputEos = true;
                    } else {
                        inBuf.put(pcmBuf, 0, n);
                        codec.queueInputBuffer(inIdx, 0, n, ptsUs, 0);
                        ptsUs += (long) n * 1000000L / (2L * 44100L); // n 字节 = n/2 样本
                    }
                }
            }
            int outIdx = codec.dequeueOutputBuffer(info, 10000);
            if (outIdx >= 0) {
                ByteBuffer outBuf = outBufs[outIdx];
                boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    byte[] frame = new byte[info.size];
                    outBuf.position(info.offset);
                    outBuf.get(frame);
                    writeAdtsHeader(fos, frame.length);
                    fos.write(frame);
                }
                codec.releaseOutputBuffer(outIdx, false);
                if (eos) outputEos = true;
            }
        }
        fis.close();
        fos.flush();
        fos.close();
        codec.stop();
        codec.release();
    }

    /** 写 7 字节 ADTS 头（AAC-LC，44.1kHz，mono）。 */
    private static void writeAdtsHeader(FileOutputStream fos, int frameLen) throws Exception {
        int profile = 1;          // AAC LC
        int sampleRateIndex = 4;  // 44100
        int channelConfig = 1;    // mono
        int fullLen = frameLen + 7;
        byte[] h = new byte[7];
        h[0] = (byte) 0xFF;
        h[1] = (byte) 0xF1;
        h[2] = (byte) (((profile & 0x3) << 6) | ((sampleRateIndex & 0xF) << 2) | ((channelConfig >> 2) & 0x1));
        h[3] = (byte) (((channelConfig & 0x3) << 6) | ((fullLen >> 11) & 0x3));
        h[4] = (byte) ((fullLen >> 3) & 0xFF);
        h[5] = (byte) (((fullLen & 0x7) << 5) | 0x1F);
        h[6] = (byte) 0xFC;
        fos.write(h);
    }

    /** 把 h264 视频 + AAC 音频封装为最终 mp4（不转码，只重新封装）。 */
    private static void muxVideoAudio(String videoPath, String aacPath, String outPath) throws Exception {
        MediaExtractor vExt = new MediaExtractor();
        vExt.setDataSource(videoPath);
        int vIdx = -1;
        for (int i = 0; i < vExt.getTrackCount(); i++) {
            MediaFormat f = vExt.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("video/")) { vIdx = i; break; }
        }
        MediaExtractor aExt = new MediaExtractor();
        aExt.setDataSource(aacPath);
        int aIdx = -1;
        for (int i = 0; i < aExt.getTrackCount(); i++) {
            MediaFormat f = aExt.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) { aIdx = i; break; }
        }
        if (vIdx < 0 || aIdx < 0) throw new RuntimeException("track not found v=" + vIdx + " a=" + aIdx);
        vExt.selectTrack(vIdx);
        aExt.selectTrack(aIdx);

        MediaMuxer muxer = new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int mv = muxer.addTrack(vExt.getTrackFormat(vIdx));
        int ma = muxer.addTrack(aExt.getTrackFormat(aIdx));
        muxer.start();

        ByteBuffer vBuf = ByteBuffer.allocate(1 << 20);
        ByteBuffer aBuf = ByteBuffer.allocate(1 << 20);
        MediaCodec.BufferInfo vInfo = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo aInfo = new MediaCodec.BufferInfo();

        boolean vDone = false, aDone = false;
        int vSize = 0, aSize = 0;
        long vPts = 0, aPts = 0;
        long aCount = 0;

        while (!vDone || !aDone) {
            if (!vDone && vSize <= 0) {
                vSize = vExt.readSampleData(vBuf, 0);
                if (vSize < 0) { vDone = true; vSize = 0; }
                else vPts = vExt.getSampleTime();
            }
            if (!aDone && aSize <= 0) {
                aSize = aExt.readSampleData(aBuf, 0);
                if (aSize < 0) { aDone = true; aSize = 0; }
                else {
                    long raw = aExt.getSampleTime();
                    // 若 extractor 不提供音频时间戳，用帧计数推算（1024 样本/帧）
                    aPts = raw > 0 ? raw : aCount * 1024 * 1000000L / 44100L;
                    aCount++;
                }
            }
            if (vDone && aDone) break;
            if (!vDone && (aDone || vPts <= aPts)) {
                vInfo.offset = 0; vInfo.size = vSize;
                vInfo.presentationTimeUs = vPts;
                vInfo.flags = vExt.getSampleFlags();
                muxer.writeSampleData(mv, vBuf, vInfo);
                vSize = 0;
                vExt.advance();
            } else if (!aDone) {
                aInfo.offset = 0; aInfo.size = aSize;
                aInfo.presentationTimeUs = aPts;
                aInfo.flags = aExt.getSampleFlags();
                muxer.writeSampleData(ma, aBuf, aInfo);
                aSize = 0;
                aExt.advance();
            }
        }
        muxer.stop();
        muxer.release();
        vExt.release();
        aExt.release();
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
