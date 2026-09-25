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

        // 2) 拦截系统截屏组合键入口
        XC_MethodHook shotHook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                long now = System.currentTimeMillis();
                if (now - lastTrigger < 1500) { p.setResult(null); return; }
                lastTrigger = now;
                XposedBridge.log("ScreenshotX: screenshot chord intercepted");
                // 尝试设置系统内部标志，告诉电源键处理这是截屏组合、不要锁屏
                trySetFlag(p.thisObject, "mScreenshotChordConsumed");
                trySetFlag(p.thisObject, "mPowerKeyConsumedByScreenshotChord");
                trySetFlag(p.thisObject, "mScreenshotChordPowerKeyUpConsumed");
                fire();
                p.setResult(null);
            }
        };
        int n = XposedBridge.hookAllMethods(pwm, "interceptScreenshotChord", shotHook).size();
        XposedBridge.log("ScreenshotX: interceptScreenshotChord hooks=" + n);

        // 3) 始终 hook 按键分发：截屏后 1.5s 内吃掉电源键 UP，阻止锁屏
        XC_MethodHook keyHook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                KeyEvent ev = (KeyEvent) p.args[0];
                if (ev == null) return;
                if (ev.getKeyCode() == KeyEvent.KEYCODE_POWER
                        && ev.getAction() == KeyEvent.ACTION_UP
                        && System.currentTimeMillis() - lastTrigger < 1500) {
                    XposedBridge.log("ScreenshotX: consume power up after screenshot");
                    p.setResult(0);
                }
            }
        };
        try {
            XposedHelpers.findAndHookMethod(pwm, "interceptKeyBeforeQueueing",
                    KeyEvent.class, int.class, keyHook);
        } catch (Throwable t) {
            try {
                XposedHelpers.findAndHookMethod(pwm, "interceptKeyBeforeQueueing",
                        KeyEvent.class, int.class, int.class, keyHook);
            } catch (Throwable ignored) {}
        }
    }

    private static void trySetFlag(Object obj, String name) {
        try { XposedHelpers.setBooleanField(obj, name, true); }
        catch (Throwable ignored) {}
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
