package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookMain implements IXposedHookLoadPackage {

    private static boolean powerDown = false;
    private static long powerTime = 0;
    private static long lastTrigger = 0;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        // 按键拦截在系统框架(android / system_server)进程
        if (!"android".equals(lp.packageName)) return;

        Class<?> pwm;
        try {
            pwm = XposedHelpers.findClass(
                    "com.android.server.policy.PhoneWindowManager", lp.classLoader);
        } catch (Throwable t) {
            XposedBridge.log("ScreenshotX: PhoneWindowManager not found");
            return;
        }

        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                KeyEvent ev = (KeyEvent) param.args[0];
                if (ev == null) return;
                int code = ev.getKeyCode();
                int action = ev.getAction();

                if (code == KeyEvent.KEYCODE_POWER) {
                    if (action == KeyEvent.ACTION_DOWN) {
                        powerDown = true;
                        powerTime = System.currentTimeMillis();
                    } else if (action == KeyEvent.ACTION_UP) {
                        powerDown = false;
                    }
                }

                if (code == KeyEvent.KEYCODE_VOLUME_DOWN
                        && action == KeyEvent.ACTION_DOWN
                        && powerDown
                        && System.currentTimeMillis() - powerTime < 600) {
                    long now = System.currentTimeMillis();
                    if (now - lastTrigger < 1000) return;
                    lastTrigger = now;

                    try {
                        Context ctx = (Context) XposedHelpers.getObjectField(
                                param.thisObject, "mContext");
                        Intent svc = new Intent();
                        svc.setClassName("io.github.gjr787878.screenshotx",
                                "io.github.gjr787878.screenshotx.ScreenshotService");
                        svc.setAction(ScreenshotService.ACTION_SHOOT);
                        ctx.startService(svc);
                    } catch (Throwable t) {
                        XposedBridge.log("ScreenshotX: startService failed: " + t);
                    }
                    // 取消系统截屏
                    param.setResult(0);
                }
            }
        };

        // 新版签名 (KeyEvent, int)
        try {
            XposedHelpers.findAndHookMethod(pwm, "interceptKeyBeforeQueueing",
                    KeyEvent.class, int.class, hook);
            XposedBridge.log("ScreenshotX: hooked (2 args)");
        } catch (Throwable t) {
            // 旧版签名 (KeyEvent, int, int)
            try {
                XposedHelpers.findAndHookMethod(pwm, "interceptKeyBeforeQueueing",
                        KeyEvent.class, int.class, int.class, hook);
                XposedBridge.log("ScreenshotX: hooked (3 args)");
            } catch (Throwable t2) {
                XposedBridge.log("ScreenshotX: hook failed: " + t2);
            }
        }
    }
}
