package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookMain implements IXposedHookLoadPackage {

    private static final String TAG = "ScreenshotX";
    private static final String LOG_FILE = "/data/local/tmp/screenshotx_diag.log";
    private static Context sysContext;
    private static long lastTrigger = 0;
    private static final Handler OWN = new Handler(Looper.getMainLooper());

    private static void log(String msg) {
        String line = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date())
                + " " + msg;
        XposedBridge.log(TAG + ": " + msg);
        try {
            FileWriter fw = new FileWriter(new File(LOG_FILE), true);
            fw.write(line + "\n");
            fw.close();
        } catch (Throwable ignored) {}
    }

    private static void clearLog() {
        try {
            FileWriter fw = new FileWriter(new File(LOG_FILE), false);
            fw.write("=== ScreenshotX diag "
                    + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
                    + " ===\n");
            fw.close();
        } catch (Throwable ignored) {}
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        clearLog();
        log("handleLoadPackage pkg=" + lp.packageName
                + " process=" + (lp.processName == null ? "null" : lp.processName));
        if (!"android".equals(lp.packageName)) {
            log("skip (not android)");
            return;
        }
        log("in system framework, start hooking");
        ClassLoader cl = lp.classLoader;

        Class<?> pwm;
        try {
            pwm = XposedHelpers.findClass(
                    "com.android.server.policy.PhoneWindowManager", cl);
            log("PhoneWindowManager found");
        } catch (Throwable t) {
            log("PhoneWindowManager NOT found: " + t);
            return;
        }

        try {
            Set<?> r = XposedBridge.hookAllMethods(pwm, "init", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.args.length > 0 && p.args[0] instanceof Context) {
                        sysContext = (Context) p.args[0];
                        log("init context captured");
                    }
                }
            });
            log("init hooks=" + r.size());
        } catch (Throwable t) {
            log("init hook failed: " + t);
        }

        try {
            Set<?> r = XposedBridge.hookAllMethods(pwm, "interceptScreenshotChord",
                    new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    long now = System.currentTimeMillis();
                    if (now - lastTrigger < 1500) {
                        log("chord debounced");
                        return;
                    }
                    lastTrigger = now;
                    long delay = 0;
                    if (p.args.length >= 2 && p.args[1] instanceof Long) {
                        delay = (Long) p.args[1];
                    }
                    log("chord HIT, delay=" + delay + ", args=" + p.args.length);
                    OWN.postDelayed(HookMain.this::fire, Math.max(0, delay));
                }
            });
            log("interceptScreenshotChord hooks=" + r.size());
        } catch (Throwable t) {
            log("interceptScreenshotChord hook failed: " + t);
        }

        try {
            Class<?> sh = XposedHelpers.findClass(
                    "com.android.internal.util.ScreenshotHelper", cl);
            XC_MethodHook block = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (System.currentTimeMillis() - lastTrigger < 3000) {
                        p.setResult(null);
                        log("blocked ScreenshotHelper " + p.method.getName());
                    }
                }
            };
            Set<?> r1 = XposedBridge.hookAllMethods(sh, "takeScreenshot", block);
            Set<?> r2 = XposedBridge.hookAllMethods(sh, "takeScreenshotInternal", block);
            log("ScreenshotHelper takeScreenshot=" + r1.size()
                    + " takeScreenshotInternal=" + r2.size());
        } catch (Throwable t) {
            log("ScreenshotHelper hook failed: " + t);
        }

        log("hooking done");
    }

    private void fire() {
        Context c = sysContext;
        if (c == null) {
            log("fire skipped, no context");
            return;
        }
        try {
            Intent svc = new Intent();
            svc.setClassName("io.github.gjr787878.screenshotx",
                    "io.github.gjr787878.screenshotx.ScreenshotService");
            svc.setAction(ScreenshotService.ACTION_SHOOT);
            c.startService(svc);
            log("fire sent");
        } catch (Throwable t) {
            log("fire failed: " + t);
        }
    }
}
