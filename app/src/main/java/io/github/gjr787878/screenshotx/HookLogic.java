package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
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
    private static ClassLoader serverCl;
    private static long lastTrigger = 0;
    private static final Handler OWN = new Handler(Looper.getMainLooper());
    private static boolean logInited = false;
    private static volatile boolean installed = false;
    private static final Set<Class<?>> hookedHelperClasses =
            new java.util.concurrent.CopyOnWriteArraySet<>();

    // system_server 内常驻的 Root shell，省去每次触发冷启动 su
    private static Process rootShellProc;
    private static DataOutputStream rootShellOs;

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
        if (installed) {
            log("install already done, skip");
            return;
        }
        serverCl = cl;
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

        // 触发点改为所有截屏的必经出口 ScreenshotHelper.takeScreenshot：
        // 直接取消系统截屏、改走自有 root screencap，不依赖各 ROM 的组合键方法名。
        try {
            Class<?> sh = XposedHelpers.findClass(
                    "com.android.internal.util.ScreenshotHelper", cl);
            hookScreenshotHelper(sh);

            // 兼容 MIUI/HyperOS 等用子类重写：实例构造后按运行时真实类补 hook
            XposedBridge.hookAllConstructors(sh, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Class<?> runtime = p.thisObject.getClass();
                    if (runtime != sh) hookScreenshotHelper(runtime);
                }
            });
            log("ScreenshotHelper ctor hooked");
        } catch (Throwable t) {
            log("ScreenshotHelper hook failed: " + t);
        }

        // 开机后尽力预建常驻 Root shell（已授权则静默成功，首次触发即快）
        new Thread(HookLogic::ensureDirectShell).start();

        installed = true;
        log("install: hooking done");
    }

    /** 在指定类（基类或 ROM 子类）上 hook 截屏入口：取消系统截屏并触发自有抓拍。 */
    private static void hookScreenshotHelper(Class<?> cls) {
        if (!hookedHelperClasses.add(cls)) return;
        XC_MethodHook replace = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                long now = System.currentTimeMillis();
                boolean fire = (now - lastTrigger) > 1500;
                lastTrigger = now;
                p.setResult(null); // 取消系统截屏
                log("intercepted " + p.method.getDeclaringClass().getSimpleName()
                        + "." + p.method.getName() + " fire=" + fire);
                if (fire) OWN.postDelayed(HookLogic::fire, 0);
            }
        };
        Set<?> a = XposedBridge.hookAllMethods(cls, "takeScreenshot", replace);
        Set<?> b = XposedBridge.hookAllMethods(cls, "takeScreenshotInternal", replace);
        log("hook ScreenshotHelper on " + cls.getName()
                + " takeScreenshot=" + a.size() + " takeScreenshotInternal=" + b.size());
    }

    /** 兜底获取系统 Context：system_server 内通过 ActivityThread.getSystemContext()，不依赖 init hook 时机。 */
    private static Context resolveSystemContext() {
        try {
            ClassLoader cl = serverCl != null ? serverCl : ClassLoader.getSystemClassLoader();
            Class<?> at = Class.forName("android.app.ActivityThread", false, cl);
            Object thread = at.getMethod("currentActivityThread").invoke(null);
            Object ctx = at.getMethod("getSystemContext").invoke(thread);
            if (ctx instanceof Context) {
                log("system context resolved via ActivityThread");
                return (Context) ctx;
            }
        } catch (Throwable t) {
            log("resolveSystemContext failed: " + t);
        }
        return null;
    }

    private static void fire() {
        // 后台线程执行，避免阻塞 system_server 主线程
        new Thread(() -> {
            // 主路径：常驻 Root shell 抓拍，后台并发预热 App 进程，开机即快
            if (rootShotPersistent()) return;

            // 兜底：拉起 App 服务（App 已打开/Root 已授权时可靠）
            Context c = sysContext;
            if (c == null) c = resolveSystemContext();
            if (c == null) {
                log("fire skipped, no context");
                return;
            }
            sysContext = c;
            try {
                Intent svc = new Intent();
                svc.setClassName("io.github.gjr787878.screenshotx",
                        "io.github.gjr787878.screenshotx.ScreenshotService");
                svc.setAction(ScreenshotService.ACTION_SHOOT);
                c.startService(svc);
                log("fire sent (service fallback)");
            } catch (Throwable t) {
                log("fire failed: " + t);
            }
        }).start();
    }

    private static void drainStatic(InputStream is) {
        new Thread(() -> {
            try {
                byte[] b = new byte[1024];
                while (is.read(b) > 0) { }
            } catch (Throwable ignored) { }
        }).start();
    }

    /** 建立（或复用）system_server 内常驻的 Root shell。 */
    private static synchronized boolean ensureDirectShell() {
        if (rootShellProc != null && rootShellOs != null) return true;
        try {
            rootShellProc = Runtime.getRuntime().exec("su");
            rootShellOs = new DataOutputStream(rootShellProc.getOutputStream());
            drainStatic(rootShellProc.getInputStream());
            drainStatic(rootShellProc.getErrorStream());
            return true;
        } catch (Throwable t) {
            rootShellProc = null;
            rootShellOs = null;
            return false;
        }
    }

    /** 通过常驻 Root shell 抓拍：后台并发预热 App 进程，抓拍完再启动编辑器（进程已就绪）。 */
    private static boolean rootShotPersistent() {
        final String shot = "/data/local/tmp/screenshotx_shot.png";
        if (!ensureDirectShell()) {
            log("direct shell unavailable");
            return false;
        }
        try {
            DataOutputStream os;
            synchronized (HookLogic.class) {
                os = rootShellOs;
            }
            if (os == null) return false;
            // 后台预热 App 进程（无界面），与抓拍并发，缩短随后编辑器冷启动
            os.writeBytes("am startservice -n io.github.gjr787878.screenshotx/.ScreenshotService &\n");
            os.writeBytes("rm -f " + shot + "\n");
            os.writeBytes("screencap -p " + shot + "\n");
            os.writeBytes("chmod 666 " + shot + "\n");
            os.writeBytes("am start -n io.github.gjr787878.screenshotx/.EditorActivity"
                    + " --es path " + shot + "\n");
            os.flush();
            log("direct shell shot dispatched");
            return true;
        } catch (Throwable t) {
            log("direct shell shot failed: " + t);
            synchronized (HookLogic.class) {
                rootShellProc = null;
                rootShellOs = null;
            }
            return false;
        }
    }
}
