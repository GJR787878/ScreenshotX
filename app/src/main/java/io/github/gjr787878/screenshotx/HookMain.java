package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.content.Intent;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookMain implements IXposedHookLoadPackage {

    private static Context sysContext;
    private static long lastTrigger = 0;

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

        // 1) 从 init() 抓系统 Context
        XposedBridge.hookAllMethods(pwm, "init", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.args.length > 0 && p.args[0] instanceof Context) {
                    sysContext = (Context) p.args[0];
                }
            }
        });

        // 2) 拦截系统截屏的「最终动作」handleScreenShot。
        //    走到这里时组合键状态机已完整执行(系统已设 mPowerKeyHandled、取消短按锁屏)，
        //    所以不会锁屏；我们只替换截屏内容，完全不碰电源键，正常锁屏不受影响。
        XC_MethodHook shotHook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                long now = System.currentTimeMillis();
                if (now - lastTrigger < 1500) { p.setResult(null); return; }
                lastTrigger = now;
                XposedBridge.log("ScreenshotX: handleScreenShot intercepted");
                fire();
                p.setResult(null); // 阻止系统截屏
            }
        };
        int n = XposedBridge.hookAllMethods(pwm, "handleScreenShot", shotHook).size();
        XposedBridge.log("ScreenshotX: handleScreenShot hooks=" + n);

        // Fallback：旧版系统没有 handleScreenShot，则拦截 interceptScreenshotChord
        if (n == 0) {
            XposedBridge.hookAllMethods(pwm, "interceptScreenshotChord", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    long now = System.currentTimeMillis();
                    if (now - lastTrigger < 1500) { p.setResult(null); return; }
                    lastTrigger = now;
                    fire();
                    p.setResult(null);
                }
            });
        }
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
