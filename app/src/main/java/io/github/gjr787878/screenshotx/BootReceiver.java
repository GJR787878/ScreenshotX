package io.github.gjr787878.screenshotx;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 开机自启：以前台服务启动截屏服务，保持进程常驻（前台状态），
 * 保证 system_server 抓拍后能立即拉起悬浮预览。
 * BOOT_COMPLETED 接收器运行期间允许启动前台服务。
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        try {
            context.startForegroundService(new Intent(context, ScreenshotService.class));
        } catch (Throwable ignored) { }
    }
}
