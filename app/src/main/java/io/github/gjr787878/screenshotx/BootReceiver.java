package io.github.gjr787878.screenshotx;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.io.DataOutputStream;

/** 开机自启：自动请求 Root 权限并预热截屏服务，加快首次触发。 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        // 开机自动请求 Root（Magisk 会弹窗授权一次，之后静默）
        new Thread(() -> {
            try {
                Process p = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(p.getOutputStream());
                os.writeBytes("exit\n");
                os.flush();
                p.waitFor();
            } catch (Throwable ignored) { }
        }).start();

        // 预热截屏服务（保持进程常驻）；后台启动受限则忽略，hook 触发时仍可拉起
        try {
            context.startService(new Intent(context, ScreenshotService.class));
        } catch (Throwable ignored) { }
    }
}
