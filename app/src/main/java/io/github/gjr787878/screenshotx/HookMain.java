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

        // 1) 从 init() 抓取系统 Context
        XposedBridge.hookAllMethods(pwm, "init", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.args.length > 0 && p.args[0] instanceof Context) {
                    sysContext = (Context) p.args[0];
                }
            }
        });

        // 2) 直接拦截系统截屏组合键的入口（最可靠）
        XC_MethodHook shotHook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                long now = System.currentTimeMillis();
                if (now - lastTrigger < 1200) { p.setResult(null); return; }
                lastTrigger = now;
                XposedBridge.log("ScreenshotX: system screenshot chord intercepted");
                fire();
                p.setResult(null); // 阻止系统截屏
            }
        };
        int n1 = XposedBridge.hookAllMethods(pwm, "interceptScreenshotChord", shotHook).size();
        XposedBridge.log("ScreenshotX: interceptScreenshotChord hooks=" + n1);

        // 3) Fallback：自己检测组合键时序
        if (n1 == 0) {
            hookKeyFallback(pwm);
        }
    }

    private void hookKeyFallback(Class<?> pwm) {
        final boolean[] power = {false};
        final long[] ptime = {0};
        XC_MethodHook h = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                KeyEvent ev = (KeyEvent) p.args[0];
                if (ev == null) return;
                if (ev.getKeyCode() == KeyEvent.KEYCODE_POWER) {
                    power[0] = ev.getAction() == KeyEvent.ACTION_DOWN;
                    if (power[0]) ptime[0] = System.currentTimeMillis();
                }
                if (ev.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN
                        && ev.getAction() == KeyEvent.ACTION_DOWN
                        && power[0] && System.currentTimeMillis()-ptime[0] < 600) {
                    long now = System.currentTimeMillis();
                    if (now-lastTrigger < 1200) return;
                    lastTrigger = now;
                    fire();
                    p.setResult(0);
                }
            }
        };
        try {
            XposedHelpers.findAndHookMethod(pwm, "interceptKeyBeforeQueueing",
                    KeyEvent.class, int.class, h);
        } catch (Throwable t) {
            try {
                XposedHelpers.findAndHookMethod(pwm, "interceptKeyBeforeQueueing",
                        KeyEvent.class, int.class, int.class, h);
            } catch (Throwable ignored) {}
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
