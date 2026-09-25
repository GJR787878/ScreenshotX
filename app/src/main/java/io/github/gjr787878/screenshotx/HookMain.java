package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookMain implements IXposedHookLoadPackage {

    private static final String TAG = "ScreenshotX";
    private static Context sysContext;
    private static long lastTrigger = 0;
    private static final Handler OWN = new Handler(Looper.getMainLooper());

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        XposedBridge.log(TAG + ": handleLoadPackage pkg=" + lp.packageName
                + " process=" + (lp.processName == null ? "null" : lp.processName));
        if (!"android".equals(lp.packageName)) return;
        XposedBridge.log(TAG + ": in system framework, start hooking");
        ClassLoader cl = lp.classLoader;

        Class<?> pwm;
        try {
            pwm = XposedHelpers.findClass(
                    "com.android.server.policy.PhoneWindowManager", cl);
            XposedBridge.log(TAG + ": PhoneWindowManager found");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": PhoneWindowManager NOT found: " + t);
            return;
        }

        // 1) 抓系统 Context
        try {
            Set<?> r = XposedBridge.hookAllMethods(pwm, "init", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.args.length > 0 && p.args[0] instanceof Context) {
                        sysContext = (Context) p.args[0];
                        XposedBridge.log(TAG + ": init context captured");
                    }
                }
            });
            XposedBridge.log(TAG + ": init hooks=" + r.size());
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": init hook failed: " + t);
        }

        // 2) 触发点
        try {
            Set<?> r = XposedBridge.hookAllMethods(pwm, "interceptScreenshotChord",
                    new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    long now = System.currentTimeMillis();
                    if (now - lastTrigger < 1500) return;
                    lastTrigger = now;
                    long delay = 0;
                    if (p.args.length >= 2 && p.args[1] instanceof Long) {
                        delay = (Long) p.args[1];
                    }
                    XposedBridge.log(TAG + ": chord HIT, delay=" + delay);
                    OWN.postDelayed(HookMain.this::fire, Math.max(0, delay));
                }
            });
            XposedBridge.log(TAG + ": interceptScreenshotChord hooks=" + r.size());
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": interceptScreenshotChord hook failed: " + t);
        }

        // 3) 取消系统截屏
        try {
            Class<?> sh = XposedHelpers.findClass(
                    "com.android.internal.util.ScreenshotHelper", cl);
            XC_MethodHook block = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (System.currentTimeMillis() - lastTrigger < 3000) {
                        p.setResult(null);
                        XposedBridge.log(TAG + ": blocked ScreenshotHelper");
                    }
                }
            };
            Set<?> r1 = XposedBridge.hookAllMethods(sh, "takeScreenshot", block);
            Set<?> r2 = XposedBridge.hookAllMethods(sh, "takeScreenshotInternal", block);
            XposedBridge.log(TAG + ": ScreenshotHelper takeScreenshot=" + r1.size()
                    + " takeScreenshotInternal=" + r2.size());
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": ScreenshotHelper hook failed: " + t);
        }

        XposedBridge.log(TAG + ": hooking done");
    }

    private void fire() {
        Context c = sysContext;
        if (c == null) {
            XposedBridge.log(TAG + ": fire skipped, no context");
            return;
        }
        try {
            Intent svc = new Intent();
            svc.setClassName("io.github.gjr787878.screenshotx",
                    "io.github.gjr787878.screenshotx.ScreenshotService");
            svc.setAction(ScreenshotService.ACTION_SHOOT);
            c.startService(svc);
            XposedBridge.log(TAG + ": fire sent");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": fire failed: " + t);
        }
    }
}
