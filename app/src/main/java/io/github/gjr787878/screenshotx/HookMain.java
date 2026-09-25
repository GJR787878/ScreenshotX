package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookMain implements IXposedHookLoadPackage {

    private static Context sysContext;
    private static long lastTrigger = 0;
    private static final int MSG_SCREENSHOT_CHORD = 16;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!"android".equals(lp.packageName)) return;

        Class<?> pwm;
        try {
            pwm = XposedHelpers.findClass(
                    "com.android.server.policy.PhoneWindowManager", lp.classLoader);
        } catch (Throwable t) {
            XposedBridge.log("ScreenshotX: PhoneWindowManager not found");
            return;
        }

        // 1) 抓系统 Context
        XposedBridge.hookAllMethods(pwm, "init", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.args.length > 0 && p.args[0] instanceof Context) {
                    sysContext = (Context) p.args[0];
                }
            }
        });

        // 2) Hook interceptScreenshotChord —— 让它正常执行(保留系统的组合键延迟窗口，
        //    电源键 up 在窗口内不锁屏)，然后安排相同时长的任务：
        //    窗口结束时取消系统截屏消息、替换成我们的截屏。
        XposedBridge.hookAllMethods(pwm, "interceptScreenshotChord", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                long delay = 0;
                if (p.args.length >= 2 && p.args[1] instanceof Long) {
                    delay = (Long) p.args[1];
                }
                long now = System.currentTimeMillis();
                if (now - lastTrigger < 1500) return;
                lastTrigger = now;

                final Handler handler = (Handler)
                        XposedHelpers.getObjectField(p.thisObject, "mHandler");
                final Runnable replace = () -> {
                    handler.removeMessages(MSG_SCREENSHOT_CHORD); // 取消系统截屏
                    XposedBridge.log("ScreenshotX: replace system screenshot");
                    fire();
                };
                // 略早于系统消息执行，确保先取消
                handler.postDelayed(replace, Math.max(0, delay - 5));
            }
        });
    }

    private void fire() {
        Context c = sysContext;
        if (c == null) return;
        try {
            Intent svc = new Intent();
            svc.setClassName("io.github.gjr787878.screenshotx",
                    "io.github.gjr787878.screenshotx.ScreenshotService");
            svc.setAction(ScreenshotService.ACTION_SHOOT);
            c.startService(svc);
        } catch (Throwable t) {
            XposedBridge.log("ScreenshotX: fire failed: " + t);
        }
    }
}
