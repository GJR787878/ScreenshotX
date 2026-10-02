package io.github.gjr787878.screenshotx;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;

/**
 * 透明中转 Activity（由 system_server 在组合键时拉起）：
 * 1. 确保 RECORD_AUDIO 已授予（内录需要；通常已由 root 静默 pm grant）
 * 2. 请求 MediaProjection 授权：SystemUI 授权对话框由模块 hook 自动批准（零交互）；
 *    hook 未生效时用户手动点“立即开始”即可（优雅降级）
 * 3. 授权结果交给 RecordService 开始录制；取消则广播通知 system_server 复位按键状态
 */
public class ProjectionRequestActivity extends Activity {

    private static final String TAG = "ScreenshotX";
    private static final int REQ_AUDIO_PERM = 101;
    private static final int REQ_PROJECTION = 200;
    private boolean launched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},
                    REQ_AUDIO_PERM);
        } else {
            launchProjection();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 无论是否授予都继续：未授予只是没有内录，视频照常
        launchProjection();
    }

    private void launchProjection() {
        if (launched) return;
        launched = true;
        MediaProjectionManager mpm = getSystemService(MediaProjectionManager.class);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                Intent svc = new Intent(this, RecordService.class)
                        .setAction(RecordService.ACTION_START)
                        .putExtra(RecordService.EXTRA_RESULT_CODE, resultCode)
                        .putExtra(RecordService.EXTRA_RESULT_DATA, data);
                startForegroundService(svc);
            } else {
                Log.d(TAG, "projection consent canceled");
                sendBroadcast(new Intent(RecordService.ACTION_RECORD_ENDED)
                        .setPackage(getPackageName()));
            }
            finish();
        }
    }
}
