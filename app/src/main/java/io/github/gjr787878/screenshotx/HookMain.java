package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookMain implements IXposedHookLoadPackage {

    private static Context sysContext;
    private static long lastTrigger = 0;
    // 自己的主 Handler，必定可用，不依赖反射系统 mHandler
    private static final Handler OWN = new Handler(Looper.getMainLooper());

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!"android".equals(lp.packageName)) return;
        ClassLoader cl = lp.classLoader;

        Class<?> pwm;
        try {
            pwm = XposedHelpers.findClass(
                    "com.android.server.policy.PhoneWindowManager", cl);
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

        // 2) 触发点：interceptScreenshotChord（已验证可命中）。
        //    【不 setResult 阻止】，让系统正常发出延迟消息 -> 组合键延迟窗口完整 -> 电源键 up 不锁屏。
        //    用自己的 Handler 安排相同时长后 fire。
        XposedBridge.hookAllMethods(pwm, "interceptScreenshotChord", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                long now = System.currentTimeMillis();
                if (now - lastTrigger < 1500) return;
                lastTrigger = now;

                long delay = 0;
                if (p.args.length >= 2 && p.args[1] instanceof Long) {
                    delay = (Long) p.args[1];
                }
                XposedBridge.log("ScreenshotX: chord detected, delay=" + delay);
                OWN.postDelayed(HookMain.this::fire, Math.max(0, delay));
            }
        });

        // 3) 可靠取消系统截屏：hook ScreenshotHelper.takeScreenshot（最终出口，方法大不内联）。
        //    只在自己刚触发的时间窗内取消，避免误伤其他截屏。
        try {
            Class<?> sh = XposedHelpers.findClass(
                    "com.android.internal.util.ScreenshotHelper", cl);
            XC_MethodHook block = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (System.currentTimeMillis() - lastTrigger < 3000) {
                        p.setResult(null);
                        XposedBridge.log("ScreenshotX: blocked system ScreenshotHelper");
                    }
                }
            };
            XposedBridge.hookAllMethods(sh, "takeScreenshot", block);
            XposedBridge.hookAllMethods(sh, "takeScreenshotInternal", block);
        } catch (Throwable t) {
            XposedBridge.log("ScreenshotX: ScreenshotHelper hook failed: " + t);
        }
    }

    private void fire() {
        Context c = sysContext;
        if (c == null) {
            XposedBridge.log("ScreenshotX: fire skipped, no context");
            return;
        }
        try {
            Intent svc = new Intent();
            svc.setClassName("io.github.gjr787878.screenshotx",
                    "io.github.gjr787878.screenshotx.ScreenshotService");
            svc.setAction(ScreenshotService.ACTION_SHOOT);
            c.startService(svc);
            XposedBridge.log("ScreenshotX: fire sent");
        } catch (Throwable t) {
            XposedBridge.log("ScreenshotX: fire failed: " + t);
        }
    }
}
