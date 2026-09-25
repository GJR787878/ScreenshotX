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

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** 实际 hook 逻辑，由现代入口 ModernEntry 调用。hook 仍用 LSPosed 兼容的 XposedBridge API。 */
public class HookLogic {

    private static final String LOG_FILE = "/data/system/screenshotx_diag.log";
    private static Context sysContext;
    private static long lastTrigger = 0;
    private static final Handler OWN = new Handler(Looper.getMainLooper());
    private static boolean logInited = false;

    public static synchronized void log(String msg) {
        String line = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date())
                + " " + msg;
        XposedBridge.log("ScreenshotX: " + msg);
        if (!logInited) {
            logInited = true;
            try {
                FileWriter h = new FileWriter(new File(LOG_FILE), false);
                h.write("=== ScreenshotX diag "
                        + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
                        + " ===\n");
                h.close();
            } catch (Throwable ignored) {}
        }
        try {
            FileWriter fw = new FileWriter(new File(LOG_FILE), true);
            fw.write(line + "\n");
            fw.close();
        } catch (Throwable ignored) {}
    }

    public static void install(ClassLoader cl) {
        log("install: start hooking in system_server");

        Class<?> pwm;
        try {
            pwm = XposedHelpers.findClass(
                    "com.android.server.policy.PhoneWindowManager", cl);
            log("PhoneWindowManager found");
        } catch (Throwable t) {
            log("PhoneWindowManager NOT found: " + t);
            return;
        }

        // 抓系统 Context
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

        // 触发点：保留系统延迟窗口（不锁屏），自己延迟相同时长后 fire
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
                    OWN.postDelayed(HookLogic::fire, Math.max(0, delay));
                }
            });
            log("interceptScreenshotChord hooks=" + r.size());
        } catch (Throwable t) {
            log("interceptScreenshotChord hook failed: " + t);
        }

        // 取消系统截屏最终出口
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

        log("install: hooking done");
    }

    private static void fire() {
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
