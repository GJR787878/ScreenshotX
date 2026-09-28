package io.github.gjr787878.screenshotx;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 开机自启：预热截屏服务以保持进程常驻，加快首次触发。
 * 不在此处请求 Root（避免开机弹 Magisk 授权提示）；
 * 抓拍所需的 root shell 由 system_server 内常驻会话负责。
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        // 预热截屏服务（保持进程常驻，服务自身不再请求 su）；
        // 后台启动受限则忽略，hook 触发时仍可拉起。
        try {
            context.startService(new Intent(context, ScreenshotService.class));
        } catch (Throwable ignored) { }
    }
}
